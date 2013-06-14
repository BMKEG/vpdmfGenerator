package edu.isi.bmkeg.vpdmf.bin;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.io.Files;

import edu.isi.bmkeg.uml.interfaces.ActionscriptInterface;
import edu.isi.bmkeg.uml.interfaces.JavaUmlInterface;
import edu.isi.bmkeg.uml.interfaces.UimaUMLInterface;
import edu.isi.bmkeg.uml.model.UMLmodel;
import edu.isi.bmkeg.uml.sources.UMLModelSimpleParser;
import edu.isi.bmkeg.utils.Converters;
import edu.isi.bmkeg.utils.mvnRunner.LocalMavenInstall;
import edu.isi.bmkeg.vpdmf.model.definitions.VPDMf;
import edu.isi.bmkeg.vpdmf.model.definitions.specs.VpdmfSpec;
import edu.isi.bmkeg.vpdmf.utils.VPDMfConverters;
import edu.isi.bmkeg.vpdmf.utils.VPDMfParser;

public class ConstructEmptyVpdmfModelMavenProject {

	public static String USAGE = "arguments: <dir> <repoId> <repoUrl>"; 
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {

		if( args.length != 3 ) {
			System.err.println(USAGE);
			System.exit(-1);
		}
		
		File dir = new File(args[0]);
		dir.mkdirs();
		
		String repoId = args[1];
		String repoUrl = args[2];

		VPDMfParser vpdmfP = new VPDMfParser();
		VPDMf top = vpdmfP.buildVpdmfSystemViews();
		
		String group = top.getGroupId();
		String artifactId = top.getArtifactId();
		String version = top.getVersion();

		// ~~~~~~~~~~~~~~~~~~~~
		// Java component build
		// ~~~~~~~~~~~~~~~~~~~~
		
		JavaUmlInterface java = new JavaUmlInterface();
		java.setUmlModel(top.getUmlModel());
		java.setBuildQuestions(true);

		File tempDir = Files.createTempDir();
		tempDir.deleteOnExit();
		String dAddr = tempDir.getAbsolutePath();
		File zip = new File(dAddr + "/temp.zip");

		java.buildJpaMavenProject(zip, null, 
				group, artifactId + "-jpa", version, 
				repoId, repoUrl);

		File buildDir = new File(dAddr + "/jpaModel");
		Converters.unzipIt(zip, buildDir);

		String srcFileName = artifactId + "-jpa-" + version + "-src.jar";
		String srcDirName = artifactId + "-jpa";
		
		String srcFileAddr = dAddr + "/jpaModel/target/" + srcFileName;
		File srcFile = new File(srcFileAddr);
		
		Converters.copyFile(zip, new File(dir.getPath() + "/" + srcFileName));
		Converters.unzipIt(zip, new File(dir.getPath() + "/" + srcDirName));

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// Flex component build and deploy
		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

		ActionscriptInterface as = new ActionscriptInterface();
		as.setUmlModel(top.getUmlModel());

		zip = new File(dAddr + "/temp2.zip");
		as.buildFlexMojoMavenProject(zip, null, group, 
				artifactId + "-as", version,
				repoId, repoUrl);
		
		Converters.unzipIt(zip, tempDir);
		
		srcFileName = artifactId + "-as-" + version + "-src.zip";
		srcDirName = artifactId + "-as-" + version + "-src";
		srcFile = new File(dir.getPath() + "/" + srcFileName);
		
		Converters.copyFile(zip, srcFile);
		Converters.unzipIt(srcFile, new File(dir.getPath() ));
		
		Converters.recursivelyDeleteFiles(tempDir);
		
		System.out.println("Model libraries built:" + dir);
		
	}

}
