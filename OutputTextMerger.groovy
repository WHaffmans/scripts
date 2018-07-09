import static groovy.io.FileType.FILES
import com.actelion.research.orbit.imageAnalysis.utils.OrbitLogAppender

OrbitLogAppender.GUI_APPENDER = false;   // no GUI (error) popups

//wijzig voor topfolder
topDirPath = ''

totalOutputFile = new File(topDirPath + "/OUTPUT_TOTAL.txt")
topDir = new File(topDirPath);
fCount = 0;
eCount = 0;
totalOutputFile.text = ""

topDir.eachDir{
	txtPath = ""
	it.eachFileMatch ~/OUTPUT\.txt$/, {txtPath = it.path}

	if(txtPath!=""){
		res = (new File(txtPath)).getText();
		totalOutputFile.append(res + '\n');
		fCount++;
	} else {
		println "No output file found in folder :" + it.path;
		eCount++
	}
}
println "Done appending " + fCount + " output files, with " + eCount + " missing files";