import static groovy.io.FileType.FILES

import com.actelion.research.orbit.imageAnalysis.utils.OrbitLogAppender
import com.actelion.research.orbit.imageAnalysis.utils.OrbitHelper
import com.actelion.research.orbit.imageAnalysis.models.OrbitModel
//import com.actelion.research.orbit.imageAnalysis.dal.ImageProviderLocal
import com.actelion.research.orbit.beans.RawDataFile
import com.actelion.research.orbit.imageAnalysis.components.RecognitionFrame
import com.actelion.research.orbit.imageAnalysis.dal.DALConfig
//import com.actelion.research.orbit.imageAnalysis.tasks.classification.ClassificationWorkerMapReduce

//Dependencies Classification Image
import com.actelion.research.orbit.imageAnalysis.components.ImageFrame
//import com.actelion.research.orbit.imageAnalysis.components.OrbitImageAnalysis
import com.actelion.research.orbit.imageAnalysis.utils.ClassImageRenderer
import com.actelion.research.orbit.imageAnalysis.utils.OrbitTiledImage2
import com.actelion.research.orbit.imageAnalysis.utils.TiledImagePainter
import javax.media.jai.TiledImage
import java.awt.image.BufferedImage

import com.actelion.research.orbit.imageAnalysis.utils.ClassificationResult
import com.actelion.research.orbit.imageAnalysis.utils.OrbitHelper

import java.awt.*
import java.util.List

//Parameters
topDirPath = 'C:/Users/dev/Desktop/Orbit batch test';
totalOutputFilename = "/OUTPUT_TOTAL.txt";
outputFilename = "/OUTPUT.txt";
classImageFilename = "/OUTPUT.jpg";
int outputWidth = 200;
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
	it.eachFileMatch ~/Classification met Ex.omo$/, {modelPath = it.path}  //TODO: check en log
	OrbitModel model = OrbitModel.LoadFromFile(modelPath); //try-catch?

    //Get current Image
	imgPath = ""
	it.eachFileMatch ~/.*\.ndpi$/, {imgPath = it.path} //TODO: check en log
    rdf = ip.registerFile(new File(imgPath), 1)
    RecognitionFrame rf = new RecognitionFrame(rdf);

    //Run Classification
    println "Start Classification";
    ClassificationResult res = OrbitHelper.Classify(rdf, rf, model, Collections.singletonList(new Point(-1, -1)), -1, null); 
    
    //Construct resultString resStr
    resStr = imgPath + " :\n";
    for (String name : res.getRatio().keySet()) {
        resStr += (name + ": " + res.getRatio().get(name) + "\n");
    }
    
    //Print and accumulate results
    println resStr + "\n";
    new File(it.path + outputFilename).text = resStr;
    totalOutputFile.append(resStr + '\n');

    //Save ClassImage
    def fn = it.path + classImageFilename;

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



