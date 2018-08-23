import static groovy.io.FileType.FILES
import com.actelion.research.orbit.imageAnalysis.dal.DALConfig
import com.actelion.research.orbit.imageAnalysis.utils.*
import com.actelion.research.orbit.imageAnalysis.tasks.*
import com.actelion.research.orbit.imageAnalysis.models.*
import com.actelion.research.orbit.imageAnalysis.models.OrbitModel
import com.actelion.research.orbit.imageAnalysis.models.ImageAnnotation
import com.actelion.research.orbit.imageAnalysis.components.RecognitionFrame
import com.actelion.research.orbit.beans.RawDataFile
import com.actelion.research.orbit.beans.RawAnnotation
import javax.media.jai.TiledImage
import javax.media.jai.JAI;
import java.awt.image.BufferedImage
import java.awt.*
import java.util.List
import com.actelion.research.orbit.utils.RawUtilsCommon;

import javax.imageio.ImageIO;
import javax.media.jai.JAI;
import javax.media.jai.TiledImage;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;

//Parameters
topDirPath = 'C:\\Users\\Hajo\\Desktop\\Orbit batch test';

totalOutputFilename = "/OUTPUT_TOTAL.json";
outputFilename = "/OUTPUT.json";
classImageFilename = "/OUTPUT";
exModelfn ="Ex.omo"
classModelfn ="Classification zonder Ex.omo"
globalExModel = true
globalClassModel = true
skipDone = false
useROI = true
classImgFactor = 16
pixelFuzzyness = 0d;

OrbitModel exModel = null
Orbitmodel classModel = null

exModelPath = topDirPath + "/" + exModelfn
exModelFile = new File(exModelPath)
if (exModelFile.exists() && globalExModel){
    exModel = OrbitModel.LoadFromFile(exModelPath);
}

classModelPath = topDirPath + "/" + classModelfn
classModelFile = new File(classModelPath)
if (classModelFile.exists() && globalClassModel){
    classModel = OrbitModel.LoadFromFile(exModelPath);
}

OrbitLogAppender.GUI_APPENDER = false; // no GUI (error) popups

startScriptTime = System.currentTimeMillis()   
totalOutputFile = new File(topDirPath + totalOutputFilename)
totalOutputFile.text = "["
topDir = new File(topDirPath); 
firstFile = true
countDoneTotal = 0
countDoneThisRun = 0

//Switch to Local Image provider
if (!DALConfig.isLocalImageProvider()){
    DALConfig.switchLocalRemoteImageProvider();
    }
ip = DALConfig.getImageProvider(); //TODO: heeft dit ook een check nodig?

topDir.eachDir{
    countDoneTotal++
    startFolderTime = System.currentTimeMillis() 
    println "Enter Folder number " + countDoneTotal +  " ("+ (startFolderTime - startScriptTime)/1000 +"):\n" + it.path; //print elke folder in de topfolder

    if(!firstFile){
        totalOutputFile.append(",")
    }
    outputFile = new File(it.path + outputFilename)
    if(outputFile.exists() && skipDone){
        println "Output file already exists, skipping..."
        totalOutputFile.append(outputFile.text + '\n')
        firstFile = false
        return
    }
    //Get current model
    modelPath = ""
    println "match model file"
    it.eachFileMatch ~/Classification met Ex.omo$/, {modelPath = it.path}  //TODO: check en log
    println "load model: " + modelPath
    OrbitModel model = OrbitModel.LoadFromFile(modelPath); //try-catch?
    if (classModel == null){
        classModel = model
        }
        
    //Get current Image
    imgPath = ""
    println "match image file"
    it.eachFileMatch ~/.*\.ndpi$/, {imgPath = it.path} //TODO: check en log
    println "create RawDataFile"
    rdf = ip.registerFile(new File(imgPath), 0);
    println "create RecognitionFrame with rdfId = " + rdf.getRawDataFileId()
    RecognitionFrame rf = new RecognitionFrame(rdf);
    
    rf.setModel(classModel);
    rmList =  ip.LoadRawMetasByRawDataFile(rdf.getRawDataFileId())
    mMeterPerPixel = rmList.find {it.name == "mMeterPerPixel"}.value.toDouble()
    pixelArea = mMeterPerPixel * mMeterPerPixel
    rf.constructClassificationImage(); //maybe del?
    rawAnno = ip.LoadRawAnnotationsByRawDataFile(rdf.rawDataFileId, RawAnnotation.ANNOTATION_TYPE_IMAGE)    
    println "create exclusionMapGen";
    if(exModel==null){
       exModel = model
       }
    
    exclusionMapGen = ExclusionMapGen.constructExclusionMap(rdf, rf, exModel, null)
    resStr = ""
    roiNumber = 1
    path = it.path
    if((rawAnno[0] != null) && useROI){
        rawAnno.each{
            anno = new ImageAnnotation(it);
            IScaleableShape roi = anno.getFirstShape()
            roi = roi.getScaledInstance(100d, new Point(0, 0))
            rf.setROI(roi);
            println "Using ROI: \n" + anno.toString()  
                //Run Classification

            println "create ClassificationWorker";
            cw = new ClassificationWorker( rdf,  rf,  classModel, true, exclusionMapGen, null) 
            println "start Worker";
            println "ROI: " + cw.getRoi().toString()
            cw.setPixelFuzzyness(pixelFuzzyness);
            cw.setDoNormalize(false);
            cw.doWork();

            println "Worker finished"

            //Construct resultString resStr
            resStr += "{\n  \"";
            resStr += cw.getTaskResult().toString().replaceAll('Classification Result: \n\nClass ratios','Filename').replaceAll(':','\" : ').replaceAll('\n',',\n  \"').replaceAll('\\[','\"').replaceAll('\\]','\"')
            resStr += ",\n  \"PixelArea\" : " + pixelArea + ",\n"
            resStr += "  \"BL\" : " + imgPath.find('(?<=BL_?)\\d{1,4}') + ",\n"
            resStr += "  \"ROI\" : " + roiNumber + "\n"
            resStr += "},\n"


            //Save ClassImage
            def fn = path + classImageFilename + "_ROI_" + roiNumber + ".png";
            Rectangle bBox = roi.getBounds();
            println bBox;
            
            TiledImage classImg = rf.getClassImage().getImage();
            bi =  new BufferedImage((int)(bBox.width/classImgFactor),(int) (bBox.height/classImgFactor), BufferedImage.TYPE_INT_RGB)
            WritableRaster r = bi.getRaster();
            for (int x = 0; x <  bi.width; x++){
			for (int y = 0; y <  bi.height; y++){
            		int ox = x*classImgFactor + (int) bBox.x
            		int oy = y*classImgFactor + (int) bBox.y
            		for(int c = 0; c<3;c++){
                		    r.setSample(x,y,c,classImg.getSample(ox,oy,c))
                	    }
			}
            }
            
            ImageIO.write(bi, "png", new File(fn))
            roiNumber++
        }

        resStr = resStr[0..-3]

        //Print and accumulate results
        println "results:\n" + resStr + "\n";

        outputFile.text = resStr;
    
        totalOutputFile.append(resStr + '\n');
        firstFile = false
        OrbitUtils.cleanUpTemp(); //Cleanup temp folder   
        println "Temp files cleaned";
        
        countDoneThisRun++
        println "Done with image " + countDoneThisRun + ": " + it.path; //print elke folder in de topfolder
        
    } else{
        println "No ROI found"
        return
    }
    
}
totalOutputFile.append("]") 
println "Run completed with " + countDoneTotal + " classifications and " + countDoneThisRun + " results written in " + (startFolderTime - startScriptTime)/1000 + " seconds."

ip.close(); // close image provider connection

