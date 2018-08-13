import static groovy.io.FileType.FILES
import com.actelion.research.orbit.imageAnalysis.dal.DALConfig
import com.actelion.research.orbit.imageAnalysis.utils.*
import com.actelion.research.orbit.imageAnalysis.tasks.*
import com.actelion.research.orbit.imageAnalysis.models.OrbitModel
import com.actelion.research.orbit.imageAnalysis.models.ImageAnnotation
import com.actelion.research.orbit.imageAnalysis.components.RecognitionFrame
import com.actelion.research.orbit.beans.RawDataFile
import com.actelion.research.orbit.beans.RawAnnotation
import javax.media.jai.TiledImage
import java.awt.image.BufferedImage
import java.awt.*
import java.util.List
import com.actelion.research.orbit.utils.RawUtilsCommon;

//Parameters
topDirPath = 'C:/Users/dev/Desktop/test3';
totalOutputFilename = "/OUTPUT_TOTAL.json";
outputFilename = "/OUTPUT.json";
classImageFilename = "/OUTPUT.jpg";
skipDone = true
useROI = true
classImgFactor = 16
//int outputWidth = 1024;
pixelFuzzyness = 0d;
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

    //Get current Image
    imgPath = ""
    println "match image file"
    it.eachFileMatch ~/.*\.ndpi$/, {imgPath = it.path} //TODO: check en log
    println "create RawDataFile"
    rdf = ip.registerFile(new File(imgPath), 0);
    println "create RecognitionFrame with rdfId = " + rdf.getRawDataFileId()
    RecognitionFrame rf = new RecognitionFrame(rdf);
    
    rf.setModel(model);
    rmList =  ip.LoadRawMetasByRawDataFile(rdf.getRawDataFileId())
    mMeterPerPixel = rmList.find {it.name == "mMeterPerPixel"}.value.toDouble()
    pixelArea = mMeterPerPixel * mMeterPerPixel
    rf.constructClassificationImage(); //maybe del?
    rawAnno = ip.LoadRawAnnotationsByRawDataFile(rdf.rawDataFileId, RawAnnotation.ANNOTATION_TYPE_IMAGE)    
    println "create exclusionMapGen";
    exclusionMapGen = ExclusionMapGen.constructExclusionMap(rdf, rf, model, null)
    resStr = ""
    roiNumber = 1
    if((rawAnno != null) && useROI){
        rawAnno.each{
            anno = new ImageAnnotation(it);
            rf.setROI(anno.getFirstShape());
            println "Using ROI: \n" + anno.toString()  
                //Run Classification

            println "create ClassificationWorker";
            cw = new ClassificationWorker( rdf,  rf,  model, true, exclusionMapGen, null) 
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
            resStr += "\"ROI\" : " + roiNumber + "\n"
            resStr += "}"

            roiNumber++
        }
        
    } else{
        println "No ROI found"
        return
    }
    resStr.replaceAll("}{","},{")

    //Print and accumulate results
    println "results:\n" + resStr + "\n";

    outputFile.text = resStr;
   
    totalOutputFile.append(resStr + '\n');
    firstFile = false

    //Save ClassImage
    def fn = it.path + classImageFilename;
    println("start loading classification image");
    final TiledImage classImg = rf.getClassImage().getImage();
    scale = (mMeterPerPixel / 0.228)
    outputWidth = (int) (scale * (classImg.getWidth() / classImgFactor) + 0.5d);
    println "outputWidth = " + outputWidth
    OrbitTiledImage2 mainImgTmp = rf.bimg.getImage();
    for (TiledImagePainter tip: rf.bimg.getMipMaps()) {
        // find a good resolution size
        if (tip.getWidth()>outputWidth)
            mainImgTmp = tip.getImage();
    }
    final OrbitTiledImage2 mainImg = mainImgTmp;
    ClassImageRenderer renderer = new ClassImageRenderer();
    int height = (int) (classImg.getHeight() * (outputWidth / (double) classImg.getWidth()));
    println("start saving classification image to disk");
    BufferedImage bi = renderer.downsample(classImg, mainImg, outputWidth, height);
    println("writing")
    renderer.saveToDisk(bi, fn);

    OrbitUtils.cleanUpTemp(); //Cleanup temp folder   
    println "Temp files cleaned";
    
    countDoneThisRun++
    println "Done with image " + countDoneThisRun + ": " + it.path; //print elke folder in de topfolder
}
totalOutputFile.append("]") 
println "Run completed with " + countDoneTotal + " classifications and " + countDoneThisRun + " results written in " + (startFolderTime - startScriptTime)/1000 + " seconds."

ip.close(); // close image provider connection



