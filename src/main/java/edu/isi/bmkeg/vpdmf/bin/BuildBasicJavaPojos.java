package edu.isi.bmkeg.vpdmf.bin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.maven.model.Model;
import org.semanticweb.owlapi.model.OWLOntology;

import com.google.common.io.Files;

import edu.isi.bmkeg.uml.builders.JavaPojoUmlBuilder;
import edu.isi.bmkeg.uml.builders.OwlUmlBuilder;
import edu.isi.bmkeg.uml.model.UMLclass;
import edu.isi.bmkeg.uml.model.UMLmodel;
import edu.isi.bmkeg.uml.sources.UMLModelSimpleParser;
import edu.isi.bmkeg.uml.utils.OwlAPIUtility;
import edu.isi.bmkeg.utils.Converters;
import edu.isi.bmkeg.vpdmf.model.definitions.specs.VpdmfSpec;
import edu.isi.bmkeg.vpdmf.utils.VPDMfParser;
import utils.VPDMfGeneratorConverters;
import utils.VPDMfModelReader;

public class BuildBasicJavaPojos {

	public static String USAGE = "arguments: [<proj1> <proj2> ... <projN>] <target-dir> <bmkeg-parent-version>"; 
	
	public static Logger logger = Logger
			.getLogger("edu.isi.bmkeg.vpdmf.bin.BuildBasicVpdmfModel");
	
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
		// Java component build
		// ~~~~~~~~~~~~~~~~~~~~
		
		JavaPojoUmlBuilder java = new JavaPojoUmlBuilder();
		java.setBuildQuestions(true);
		
		java.setUmlModel(model);

		File tempDir = Files.createTempDir();
		tempDir.deleteOnExit();
		String dAddr = tempDir.getAbsolutePath();
		File zip = new File(dAddr + "/temp.zip");

		java.buildMavenProject(zip, null, 
				group, artifactId + "-pojo", version, 
				bmkegParentVersion);

		File buildDir = new File(dAddr + "/pojoModel");
		Converters.unzipIt(zip, buildDir);

		String srcFileName = artifactId + "-pojo-" + version + "-src.jar";
		String srcDirName = artifactId + "-pojo";
		
		Converters.copyFile(zip, new File(dir.getPath() + "/" + srcFileName));
		Converters.unzipIt(zip, new File(dir.getPath() + "/" + srcDirName));
				
		Converters.recursivelyDeleteFiles(tempDir);
		
		logger.info("Java POJOs source:" + dir.getPath() + "/" + srcDirName);

	}
	
	
}
