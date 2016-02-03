package utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.maven.model.Model;

import edu.isi.bmkeg.uml.model.UMLmodel;
import edu.isi.bmkeg.uml.sources.UMLModelSimpleParser;
import edu.isi.bmkeg.vpdmf.model.definitions.specs.VpdmfSpec;
import edu.isi.bmkeg.vpdmf.utils.VPDMfParser;

public class VPDMfModelReader {
	
	public static Logger logger = Logger
			.getLogger("edu.isi.bmkeg.vpdmf.utils.VPDMfModelReader");
	
	private List<File> viewFiles = new ArrayList<File>();
	private List<File> dataFiles = new ArrayList<File>();
	private UMLmodel model;
	private String group;
	private String artifactId;
	private String version;
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public VPDMfModelReader(List<File> pomFiles, File dir, String bmkegParentVersion) 
			throws Exception {

		dir.mkdirs();

		File firstPom = pomFiles.get(0);
		Model firstPomModel = VPDMfGeneratorConverters.readModelFromPom(firstPom);
		VpdmfSpec firstSpecs = VPDMfGeneratorConverters.readVpdmfSpecFromPom(firstPomModel);
		
		Iterator<File> it = pomFiles.iterator();
		while (it.hasNext()) {
			File pomFile = it.next();

			//
			// parse the specs files
			//
			Model pomModel = VPDMfGeneratorConverters.readModelFromPom(pomFile);
			VpdmfSpec vpdmfSpec = VPDMfGeneratorConverters.readVpdmfSpecFromPom(pomModel);

			// Model file
			String modelPath = vpdmfSpec.getModel().getPath();
			String modelType = vpdmfSpec.getModel().getType();
			String modelUrl = vpdmfSpec.getModel().getUrl();

			File modelFile = new File(pomFile.getParent() + "/" + modelPath);

			// View directory
			String viewsPath = vpdmfSpec.getViewsPath();
			File viewsDir = new File(pomFile.getParent() + "/" + viewsPath);
			viewFiles.addAll(VPDMfParser.getAllSpecFiles(viewsDir));

			// Data file
			File data = null;
			if (vpdmfSpec.getData() != null) {
				String dataPath = vpdmfSpec.getData().getPath();
				data = new File(pomFile.getParent() + "/" + dataPath);
				if (!data.exists())
					data = null;
				else
					dataFiles.add(data);
			}

			if (data != null)
				System.out.println("Data File: " + data.getPath());

			UMLModelSimpleParser p = new UMLModelSimpleParser(
					UMLmodel.XMI_MAGICDRAW);
			p.parseUMLModelFile(modelFile);
			UMLmodel m = p.getUmlModels().get(0);

			if (model == null) {
				
				model = m;
				model.setUrl(modelUrl);
				
			} else {

				model.mergeModel(m);
			
			}

		}
		
		this.group = firstSpecs.getGroupId();
		this.artifactId = firstSpecs.getArtifactId();
		this.version = firstSpecs.getVersion();

	}

	public List<File> getViewFiles() {
		return viewFiles;
	}

	public void setViewFiles(List<File> viewFiles) {
		this.viewFiles = viewFiles;
	}

	public List<File> getDataFiles() {
		return dataFiles;
	}

	public void setDataFiles(List<File> dataFiles) {
		this.dataFiles = dataFiles;
	}

	public UMLmodel getModel() {
		return model;
	}

	public void setModel(UMLmodel model) {
		this.model = model;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}
	

}
