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

OrbitLogAppender.GUI_APPENDER = false;   // no GUI (error) popups

topDirPath = 'C:\\Users\\dev\\Desktop\\Orbit batch test'
totalOutputFile = new File(topDirPath + "\\OUTPUT_TOTAL.txt")
def topDir = new File(topDirPath); //wijzig voor top folder
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
    //Classify(
        // final RawDataFile rdf, 
        // final RecognitionFrame _rf, 
        // final OrbitModel _model, 
        // List<Point> tiles, 
        // int numThreads, 
        // IScaleableShape overrideROI)
    println "Start Classification";
    ClassificationResult res = OrbitHelper.Classify(rdf, rf, model, Collections.singletonList(new Point(-1, -1)), -1, null); 
    for (String name : res.getRatio().keySet()) {
        println (name + ": " + res.getRatio().get(name));
    }

    new File(it.path + "\\OUTPUT.txt").text = res.toString();
    totalOutputFile.append(res.toString() + '\n');

    /**
    * Saves a downscaled classification image to disk. Similar to "Tools->Save classification image" functionality.
    */

    int width = 200;
    def fn = it.path + "\\OUTPUT.jpg";

    
    final TiledImage classImg = rf.getClassImage().getImage();
    OrbitTiledImage2 mainImgTmp = rf.bimg.getImage();
    for (TiledImagePainter tip: rf.bimg.getMipMaps()) {
        // find a good resolution size
        if (tip.getWidth()>width)
            mainImgTmp = tip.getImage();
    }
    final OrbitTiledImage2 mainImg = mainImgTmp;
    ClassImageRenderer renderer = new ClassImageRenderer();
    int height = (int) (classImg.getHeight() * (width / (double) classImg.getWidth()));
    println("start saving classification image to disk");
    BufferedImage bi = renderer.downsample(classImg, mainImg, width, height);
    println("writing")
    renderer.saveToDisk(bi, fn);
    println "Done with: " + it.path; //print elke folder in de topfolder

    }
ip.close(); // close image provider connection



