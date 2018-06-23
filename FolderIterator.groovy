import static groovy.io.FileType.FILES
import com.actelion.research.orbit.imageAnalysis.dal.DALConfig
import com.actelion.research.orbit.imageAnalysis.utils.*
import com.actelion.research.orbit.imageAnalysis.tasks.*
import com.actelion.research.orbit.imageAnalysis.models.OrbitModel
import com.actelion.research.orbit.imageAnalysis.components.RecognitionFrame
import com.actelion.research.orbit.beans.RawDataFile
import javax.media.jai.TiledImage
import java.awt.image.BufferedImage
import java.awt.*
import java.util.List



//Parameters
topDirPath = 'C:/Users/willem/Desktop/Orbit batch test';
totalOutputFilename = "/OUTPUT_TOTAL.txt";
outputFilename = "/OUTPUT.txt";
classImageFilename = "/OUTPUT.jpg";
int outputWidth = 1024;
OrbitLogAppender.GUI_APPENDER = false; // no GUI (error) popups

totalOutputFile = new File(topDirPath + totalOutputFilename)
topDir = new File(topDirPath); 

//Switch to Local Image provider
if (!DALConfig.isLocalImageProvider()){
    DALConfig.switchLocalRemoteImageProvider();
    }
ip = DALConfig.getImageProvider(); //TODO: heeft dit ook een check nodig?

topDir.eachDir{
    //TODO: resultaat file aanwezig? skip werkt continue in een lambda?
    println "Enter Folder: " + it.path; //print elke folder in de topfolder

    //Get current model
    modelPath = ""
    println "match model file"
    it.eachFileMatch ~/Classification met Ex.omo$/, {modelPath = it.path}  //TODO: check en log
    println "load model"
    OrbitModel model = OrbitModel.LoadFromFile(modelPath); //try-catch?

    //Get current Image
    imgPath = ""
    println "match image file"
    it.eachFileMatch ~/.*\.ndpi$/, {imgPath = it.path} //TODO: check en log
    println "create RawDataFile"
    rdf = ip.registerFile(new File(imgPath), 0);
    println "create RecognitionFrame"
    RecognitionFrame rf = new RecognitionFrame(rdf);
    
    rf.setModel(model);
    rf.constructClassificationImage();

    //Run Classification
    println "create exclusionMapGen";
    //ClassificationResult res = OrbitHelper.Classify(rdf, rf, model, Collections.singletonList(new Point(-1, -1)), -1, null); 

    exclusionMapGen = ExclusionMapGen.constructExclusionMap(rdf, rf, model, null)
    println "create ClassificationWorker";
    cw = new ClassificationWorker( rdf,  rf,  model, true, exclusionMapGen, null) 
    println "start Worker"
    cw.doWork();
    //println "wait for worker"
    //OrbitUtils.waitForWorker(cw);
    println "Worker finished"

    //Construct resultString resStr
    resStr = imgPath + " :\n";
    resStr += cw.getTaskResult().toString()
    //Print and accumulate results
    println "results:\n" + resStr + "\n";
    new File(it.path + outputFilename).text = resStr;
    totalOutputFile.append(resStr + '\n');

    println "constructClassificationImage"
    //rf.constructClassificationImage();

    //Save ClassImage
    def fn = it.path + classImageFilename;
    println("start loading classification image");
    final TiledImage classImg = rf.getClassImage().getImage();
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
    println "Done with: " + it.path; //print elke folder in de topfolder
}
    
ip.close(); // close image provider connection



