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
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;

//Parameters
topDirPath = '/home/willem/dev/test';
totalOutputFilename = "/OUTPUT_TOTAL.json";
outputFilename = "/OUTPUT.json";
classImageFilename = "/OUTPUT";
exModelfn = ""        //"Ex.omo"
classModelfn = ""    //"Classification zonder Ex.omo"
skipDone = false
useROI = true
pixelFuzzyness = 0d;
maxSize = 4000
minF = 4
opacity = 0.30

//End of parameters

globalExModel = exModelfn != ""
globalClassModel = classModelfn != ""
OrbitModel exModel = null
OrbitModel classModel = null

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
def String timer(){
	return "["+(System.currentTimeMillis() - startScriptTime)/1000 + "] "
	}
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
    println timer() + "Folder(" + countDoneTotal  +"): " + it.path; //print elke folder in de topfolder
   
    if(!firstFile){
        totalOutputFile.append(",")
    }
    outputFile = new File(it.path + outputFilename)
    if(outputFile.exists() && skipDone){
        println timer() + "Output file already exists, skipping..."
        totalOutputFile.append(outputFile.text + '\n')
        firstFile = false
        return
    }
    //Get current model
   if (!globalClassModel){
        modelPath = ""
        it.eachFileMatch ~/Classification met Ex.omo$/, {modelPath = it.path}  //TODO: check en log
        
        classModel = OrbitModel.LoadFromFile(modelPath); //try-catch?
        //println timer() + classModel
        }

    //Get current Image
    imgPath = ""
    it.eachFileMatch ~/.*\.ndpi$/, {imgPath = it.path} //TODO: check en log
    rdf = ip.registerFile(new File(imgPath), 0);
    println timer()+ "rdfId = " + rdf.getRawDataFileId()
    RecognitionFrame rf = new RecognitionFrame(rdf);

    rf.setModel(classModel);
    rmList =  ip.LoadRawMetasByRawDataFile(rdf.getRawDataFileId())
    mMeterPerPixel = rmList.find {it.name == "mMeterPerPixel"}.value.toDouble()
    pixelArea = mMeterPerPixel * mMeterPerPixel
    rf.constructClassificationImage(); //maybe del?

    rf.getClassShapes().find{ it.getName() == "Background"}.setColor(Color.BLACK)
    rf.getClassShapes().find{ it.getName() == "Ery"}.setColor(Color.RED)
    rf.getClassShapes().find{ it.getName() == "FibriPlate"}.setColor(Color.GREEN)
    rf.getClassShapes().find{ it.getName() == "Leuko"}.setColor(Color.BLUE)
    
    rawAnno = ip.LoadRawAnnotationsByRawDataFile(rdf.rawDataFileId, RawAnnotation.ANNOTATION_TYPE_IMAGE)
    if(!globalExModel){
       exModel = classModel
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
              
                //Run Classification

            cw = new ClassificationWorker( rdf,  rf,  classModel, true, exclusionMapGen, null) 
            println timer()+"Classify ROI: " + roiNumber;
            bBox = roi.getBounds();
            println timer() + "Bounding box: " + bBox;
            cw.setPixelFuzzyness(pixelFuzzyness);
            cw.setDoNormalize(false);
            cw.doWork();

            println timer()+"Done!"

            //Construct resultString resStr
            resStr += "{\n  \"";
            resStr += cw.getTaskResult().toString().replaceAll('Classification Result: \n\nClass ratios','Filename').replaceAll(':','\" : ').replaceAll('\n',',\n  \"').replaceAll('\\[','\"').replaceAll('\\]','\"')
            resStr += ",\n  \"PixelArea\" : " + pixelArea + ",\n"
            resStr += "  \"BL\" : " + imgPath.find('(?<=BL_?)\\d{1,4}') + ",\n"
            resStr += "  \"ROI\" : " + roiNumber + "\n"
            resStr += "},\n"


            //Save ClassImage
            
           

            classImg = rf.getClassImage().getImage();
            ori = rf.bimg.getImage()
            
            classImgFactor = (int) Math.max(Math.ceil(Math.max(bBox.width/maxSize, bBox.height/maxSize)),minF)
            println timer() + "classImgFactor = " + classImgFactor
            fn = path + classImageFilename + "_ROI_" + roiNumber + "_Overlay_F"+classImgFactor+".png";
            bi =  new BufferedImage((int)(bBox.width/classImgFactor),(int) (bBox.height/classImgFactor), BufferedImage.TYPE_INT_RGB)
            r = bi.getRaster();
            println timer() + "Prepare Overlay"
            for (int x = 0; x <  bi.width; x++){
                for (int y = 0; y <  bi.height; y++){
                    int ox = x*classImgFactor + (int) bBox.x
                    int oy = y*classImgFactor + (int) bBox.y
                    
                    for(int c = 0; c<3;c++){
                        //sample = classImg.getSample(ox,oy,2)==255?ori.getSample(ox,oy,c):0
                        sample = classImg.getSample(ox,oy,c)*opacity + ori.getSample(ox,oy,c)*(1-opacity)
                        r.setSample(x,y,c,sample)
                    }
                        
                }
            }
		  println timer() + "Write Overlay"
            ImageIO.write(bi, "png", new File(fn))
            
            classes = ["Ery","FibriPlate","Leuko"]
            
                        //Save ClassImage
            for( int cl = 0; cl<3; cl++ ){  
            	 println timer() + "Prepare " + classes[cl]          
                fn = path + classImageFilename + "_ROI_" + roiNumber + "_"+ classes[cl] + "_F"+ classImgFactor +".png";
                bi =  new BufferedImage((int)(bBox.width/classImgFactor),(int) (bBox.height/classImgFactor), BufferedImage.TYPE_INT_RGB)
                r = bi.getRaster();
                for (int x = 0; x <  bi.width; x++){
                    for (int y = 0; y <  bi.height; y++){
                        int ox = x*classImgFactor + (int) bBox.x
                        int oy = y*classImgFactor + (int) bBox.y
                        
                        for(int c = 0; c<3;c++){
                            sample = classImg.getSample(ox,oy,cl) == 255 ? ori.getSample(ox,oy,c) : 0
                            //sample = classImg.getSample(ox,oy,c)*opacity + ori.getSample(ox,oy,c)*(1-opacity)
                            r.setSample(x,y,c,sample)
                        }
                    }
                }
                println timer() + "Write " + classes[cl] 
                ImageIO.write(bi, "png", new File(fn))
            }
            roiNumber++
        }

        resStr = resStr[0..-3]

        //Print and accumulate results
        println timer() + "results:\n" + resStr + "\n";

        outputFile.text = resStr;

        totalOutputFile.append(resStr + '\n');
        firstFile = false
        OrbitUtils.cleanUpTemp(); //Cleanup temp folder   
        println timer() + "Temp files cleaned";

        countDoneThisRun++
        println timer() + "Done with image " + countDoneThisRun + ": " + it.path; //print elke folder in de topfolder

    } else {
        println timer() + "No ROI found"
        return
    }
}
totalOutputFile.append("]") 
println "Run completed with " + countDoneTotal + " classifications and " + countDoneThisRun + " results written in " + timer() + " seconds."

ip.close(); // close image provider connection

