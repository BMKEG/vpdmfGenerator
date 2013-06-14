package edu.isi.bmkeg.vpdmf.controller.archiveBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.google.common.io.Files;

import edu.isi.bmkeg.uml.interfaces.ActionscriptInterface;
import edu.isi.bmkeg.uml.interfaces.JavaUmlInterface;
import edu.isi.bmkeg.uml.interfaces.MysqlUmlInterface;
import edu.isi.bmkeg.uml.interfaces.UimaUMLInterface;
import edu.isi.bmkeg.uml.model.UMLmodel;
import edu.isi.bmkeg.uml.utils.UMLArchiveFileBuilder;
import edu.isi.bmkeg.utils.Converters;
import edu.isi.bmkeg.utils.xml.XmlBindingTools;
import edu.isi.bmkeg.vpdmf.model.definitions.VPDMf;
import edu.isi.bmkeg.vpdmf.model.definitions.ViewDefinition;
import edu.isi.bmkeg.vpdmf.model.definitions.specs.VpdmfSpec;
import edu.isi.bmkeg.vpdmf.solr.VPDMfSolrUtils;
import edu.isi.bmkeg.vpdmf.utils.VPDMfConverters;
import edu.isi.bmkeg.vpdmf.utils.VPDMfExcelEngine;

/**
 * Class to generate and read VPDMf archive files.
 * 
 * @author burns
 * 
 */
public class VPDMfArchiveFileBuilder extends UMLArchiveFileBuilder {

	private File tempUnzippedDirectory;
	
	private Map<String, File> filesInZip;
	
	private Map<String, File> cleanUp = new HashMap<String, File>();

	private VPDMfExcelEngine xl = new VPDMfExcelEngine();

	/**
	 * Builds a VPDMf archive file ('*_VPDMf.zip') from (a) the model, (b) the
	 * views and (c) either the excel spreadsheet with the data or a directory
	 * of tab delimited *.txt files saved from a spreadsheet.
	 * 
	 * Note, if the varFile is set to a directory, the archive will be generated
	 * within the directory with a timestamped filename, otherwise the target
	 * file is simply specified there. If a file already exists at that
	 * location, it will be overwritten.
	 * 
	 * The archive is formatted as a zip file and contains (a) the source of the
	 * UMLmodel (model.xml) ... and maybe a jar with all the POJOs of the
	 * UMLmodel that can be read using reflection... (b) all views (views
	 * directory) (c) an excel spreadsheet of the data in the system (d)
	 * subsidiary mysql files (1) the script that generates the database (2) the
	 * script that uploads data from the files (3) a set of datafiles that
	 * contain the data in an appropriately delimited manner.
	 * 
	 * @param modelFile
	 * @param viewDirectory
	 * @param excelSpreadsheet
	 * @param varFile
	 * @throws IOException
	 */
	public void buildArchiveFile(VpdmfSpec vpdmfSpec, VPDMf top, 
			List<File> dataDirsOrFiles, File varFile,
			String repoId, String repoUrl) throws Exception {

		String groupId = top.getGroupId();
		String artifactId = top.getArtifactId();
		String version = top.getVersion();
		
		File targetDir = varFile.getParentFile();
		if (varFile.isDirectory()) {

			targetDir = varFile;

			Date now = Calendar.getInstance().getTime();
			Timestamp ts = new java.sql.Timestamp(now.getTime());
			String date = ts.toString();
			date = date.substring(0, date.indexOf(" "));

			String varFileName = top.getUmlModel().getName() + "_" + date + "-"
					+ ts.getHours() + ts.getMinutes() + "_VPDMf.zip";

			varFile = new File(targetDir.getAbsolutePath() + "/" + varFileName);

		}

		filesInZip = new HashMap<String, File>();
		String commandsString = "";

		UMLmodel m = top.getUmlModel();

		tempUnzippedDirectory = Files.createTempDir();
		tempUnzippedDirectory.deleteOnExit();
		String dAddr = tempUnzippedDirectory.getAbsolutePath();

		//
		// Write the model file to this temporary location
		//
		String suffix = ".tmp";
		if (m.getSourceType().equals(UMLmodel.XMI_MAGICDRAW))
			suffix = "_mgd.xml";
		else if (m.getSourceType().equals(UMLmodel.XMI_POSEIDON))
			suffix = "_pos.xml";

		File uml = new File(dAddr + "/" + m.getName() + suffix);
		FileOutputStream fos = new FileOutputStream(uml);
		fos.write(m.getSourceData());
		fos.close();
		
		filesInZip.put(uml.getName(), uml);
		uml.deleteOnExit();
		
		//
		// Write the VpdmfSpec files to the archive.
		//
		File vpdmfSpecFile = new File(dAddr + "/vpdmf.xml");    	
		FileWriter fw = new FileWriter(vpdmfSpecFile);
    	StringWriter writer = new StringWriter();
		XmlBindingTools.generateXML(vpdmfSpec, writer);		
		String str = writer.toString();
		fw.write(str);
		fw.close();
		
		filesInZip.put( vpdmfSpecFile.getName(), vpdmfSpecFile);	
		vpdmfSpecFile.deleteOnExit();
		
		//
		// Write the ViewSpec files to the archive.
		//
		Iterator<ViewDefinition> vdIt = top.getViews().values().iterator();
		while (vdIt.hasNext()) {
			ViewDefinition vd = vdIt.next();

			suffix = "-vw.xml";

			File vSpect = new File(dAddr + "/" + vd.getName() + suffix);

			FileUtils.writeStringToFile(vSpect, vd.getSpecification());

			filesInZip.put("views/" + vSpect.getName(), vSpect);
			vSpect.deleteOnExit();

		}

		//
		// Dump whole VPDMf model as serialized object to file
		//
		InputStream is = new ByteArrayInputStream(
				VPDMfConverters.vpdmfObjectToByteArray(top));
		suffix = "vpdmf.bin";
		File vpdmfFile = new File(dAddr + "/" + suffix);
		fos = new FileOutputStream(vpdmfFile);
		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = is.read(buf)) > 0) {
			fos.write(buf, 0, len);
		}
		fos.close();
		filesInZip.put(vpdmfFile.getName(), vpdmfFile);
		vpdmfFile.deleteOnExit();

		//
		// Save the sql to this location too.
		//
		MysqlUmlInterface mysql = new MysqlUmlInterface();
		mysql.setUmlModel(m);

		String sql = mysql.generateSqlForModel("\\.model\\.");

		File buildFile = new File(dAddr + "/build.sql");
		FileUtils.writeStringToFile(buildFile, sql);
		filesInZip.put("sqlFiles/build.sql", buildFile);
		buildFile.deleteOnExit();

		//
		// WE NO LONGER PUT ORIGINAL DATA FILES IN THE ARCHIVE, ONLY THE SQL DAT FILES
		//
		if (dataDirsOrFiles == null) {
			File data= new File(dAddr + "/data.xls");

			FileOutputStream out = new FileOutputStream(data);
			HSSFWorkbook wb = xl.generateBlankExcelSpreadsheetFromVPDMf(top,
					"\\.model\\.");
			wb.write(out);
			out.close();

		} else {
		
			Iterator<File> dataIt = dataDirsOrFiles.iterator();
			while( dataIt.hasNext() ) {
				File data = dataIt.next();
			
				if (data.isDirectory()) {
		
					filesInZip.putAll(xl.generateSQLDataFilesFromDirectory(
							m, data, tempUnzippedDirectory
							));

				} else {

					//
					// Validate the XL file before putting it into the archive by
					// unpacking it
					// and setting up data in batch load files.
					//
					filesInZip.putAll(xl.generateSQLDataFilesFromExcel(m,
							data, tempUnzippedDirectory));
		
				}
		
			}
		
		}
		
				// Build JPA classes for the domain to act as intermediaries for the
		// model
		// if you need to instantiate java object graphs for the views. Or if
		// you'd prefer to write your own functions against the underlying
		// database.
		
		File jar = new File(varFile.getParentFile().getAbsolutePath() + "/"
				+ top.getUmlModel().getName() + "-jpa.jar");

		File srcJar = new File(varFile.getParentFile().getAbsolutePath() + "/"
				+ top.getUmlModel().getName() + "-src.jar");

		String url = top.getUmlModel().getUrl();
		if( url == null || url.length() == 0)
			url = "edu.isi.bmkeg";
		
		JavaUmlInterface java = new JavaUmlInterface();
		if( top.getUimaPkgPattern() != null && top.getUimaPkgPattern().length() > 0  ) {
			java = new UimaUMLInterface( top.getUimaPkgPattern() );
		}	
		
		java.setUmlModel(m);
		
		java.buildJpaMavenProject(srcJar, jar, groupId, artifactId + "-jpa", version, repoId, repoUrl);
		
		filesInZip.put(srcJar.getName(), srcJar);
		srcJar.deleteOnExit();
		
		filesInZip.put(jar.getName(), jar);
		jar.deleteOnExit();
					 
		//
		// Add SOLR XML schema files for each view in the model .
		//
		if( top.getViewsToIndex().size() > 0 ) {
			VPDMfSolrUtils solr = new VPDMfSolrUtils();
			String[] views = top.getViewsToIndex().toArray(new String[0]);
			File solrZipFile = new File(dAddr + "/" + top.getUmlModel().getName() + "_solr.zip");
			solr.buildSolrSpecZip(solrZipFile, views, top);
			
			filesInZip.put(solrZipFile.getName(), solrZipFile);	
			solrZipFile.deleteOnExit();
		
		}
		
		if (varFile.exists()) {
			varFile.delete();
		}

		Converters.zipIt(filesInZip, varFile);
		Converters.recursivelyDeleteFiles(tempUnzippedDirectory);

	}

}
