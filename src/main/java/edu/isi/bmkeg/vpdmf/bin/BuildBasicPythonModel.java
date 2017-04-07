package edu.isi.bmkeg.vpdmf.bin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.google.common.io.Files;

import edu.isi.bmkeg.uml.builders.PythonUmlBuilder;
import edu.isi.bmkeg.uml.model.UMLmodel;
import edu.isi.bmkeg.utils.Converters;
import utils.VPDMfModelReader;

public class BuildBasicPythonModel {

	public static String USAGE = "arguments: [<proj1> <proj2> ... <projN>] <target-dir> <bmkeg-parent-version>"; 
	
	public static Logger logger = Logger
			.getLogger("edu.isi.bmkeg.vpdmf.bin.BuildBasicPythonModel");
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {

		if( args.length < 3 ) {
			System.err.println(USAGE);
			System.exit(-1);
		}

		List<File> pomFiles = new ArrayList<File>();
		for (int i = 0; i < args.length - 2; i++) {
			File pomFile = new File(args[i].replaceAll("\\/$", "") + "/pom.xml");	
			pomFiles.add(pomFile);
		}
		File dir = new File(args[args.length - 2]);
		String bmkegParentVersion = args[args.length - 1];

		VPDMfModelReader reader = new VPDMfModelReader(pomFiles, dir, bmkegParentVersion);
		
		List<File> viewFiles = reader.getViewFiles();
		List<File> dataFiles = reader.getDataFiles();
		UMLmodel model = reader.getModel();
		
		String group = reader.getGroup();
		String artifactId = reader.getArtifactId();
		String version = reader.getVersion();

		// ~~~~~~~~~~~~~~~~~~~~
		// Python component build
		// ~~~~~~~~~~~~~~~~~~~~
		
		PythonUmlBuilder python = new PythonUmlBuilder();
		
		python.setUmlModel(model);

		File tempDir = Files.createTempDir();
		tempDir.deleteOnExit();
		String dAddr = tempDir.getAbsolutePath();
		File zip = new File(dAddr + "/temp.zip");

		python.buildPythonProject(zip, null, 
				group, artifactId + "-py", version, 
				bmkegParentVersion);

		File buildDir = new File(dAddr + "/pyModel");
		Converters.unzipIt(zip, buildDir);

		String srcFileName = artifactId + "-python-" + version + "-src.zip";
		String srcDirName = artifactId + "-python";
		
		Converters.copyFile(zip, new File(dir.getPath() + "/" + srcFileName));
		Converters.unzipIt(zip, new File(dir.getPath() + "/" + srcDirName));
				
		Converters.recursivelyDeleteFiles(tempDir);
		
		logger.info("Python source:" + dir.getPath() + "/" + srcDirName);

	}
	
	
}
