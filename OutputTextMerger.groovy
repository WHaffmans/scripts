import static groovy.io.FileType.FILES

import com.actelion.research.orbit.imageAnalysis.utils.OrbitLogAppender





OrbitLogAppender.GUI_APPENDER = false;   // no GUI (error) popups

topDirPath = 'C:\\Users\\dev\\Desktop\\Orbit batch test'
totalOutputFile = new File(topDirPath + "\\OUTPUT_TOTAL.txt")
def topDir = new File(topDirPath); //wijzig voor top folder

topDir.eachDir{
    
    
    println "Enter Folder: " + it.path; //print elke folder in de topfolder
   


	txtPath = ""
	it.eachFileMatch ~/OUTPUT\.txt$/, {txtPath = it.path} 


	res = (new File(txtPath)).getText();

	   
    totalOutputFile.append(res + '\n');

    }






