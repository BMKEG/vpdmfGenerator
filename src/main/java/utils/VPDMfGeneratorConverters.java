package utils;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.Properties;

import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import edu.isi.bmkeg.vpdmf.model.definitions.specs.DataSpec;
import edu.isi.bmkeg.vpdmf.model.definitions.specs.ModelSpec;
import edu.isi.bmkeg.vpdmf.model.definitions.specs.VpdmfSpec;

public class VPDMfGeneratorConverters {
	
	public static Model readModelFromPom(File pomXmlFile) throws Exception {
		
		Model pomModel = null;

		Reader reader = new FileReader(pomXmlFile);
		try {
		    MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
		    pomModel = xpp3Reader.read(reader);
		} finally {
		    reader.close();
		}
		
		return pomModel;
		
	}
	
	public static VpdmfSpec readVpdmfSpecFromPom(Model pomModel) throws Exception {
		
		Properties prop = pomModel.getProperties();
		if( !prop.containsKey("vpdmf.viewsPath") ||
				!prop.containsKey("vpdmf.model.type") ||
				!prop.containsKey("vpdmf.model.path") ||
				!prop.containsKey("vpdmf.model.url") ) {
			System.out.println(prop.toString());
			throw new Exception("Must specify all these properties: vpdmf.viewsPath, " +
					"vpdmf.model.type, vpdmf.model.path, vpdmf.model.url");
		}
		
		VpdmfSpec vpdmfSpec = new VpdmfSpec();
		vpdmfSpec.setArtifactId( pomModel.getArtifactId() );
		vpdmfSpec.setGroupId( pomModel.getGroupId() );
		vpdmfSpec.setVersion( pomModel.getVersion() );
		
		vpdmfSpec.setViewsPath(prop.getProperty("vpdmf.viewsPath"));

		ModelSpec m = new ModelSpec();
		vpdmfSpec.setModel(m);
		
		m.setName( pomModel.getArtifactId() );
		m.setType( prop.getProperty("vpdmf.model.type") );
		m.setPath( prop.getProperty("vpdmf.model.path") );
		m.setUrl( prop.getProperty("vpdmf.model.url") );
		
		DataSpec d = new DataSpec();
		vpdmfSpec.setData(d);
	
		d.setPath( prop.getProperty("vpdmf.data.path") );
		d.setType( prop.getProperty("vpdmf.data.type") );
		
		String solrViews = prop.getProperty("vpdmf.solr.views");
		if( solrViews != null && solrViews.length() > 0 ) {
			String[] vv = solrViews.split(",");
			for(int i=0; i<vv.length; i++) {
				vpdmfSpec.getSolrViews().add(vv[i]);
			}
		}

		String uimaPkgPattern = prop.getProperty("vpdmf.uimaPkgPattern");
		if( uimaPkgPattern != null && uimaPkgPattern.length() > 0 ) {
			vpdmfSpec.setUimaPackagePattern( uimaPkgPattern );
		}
		
		return vpdmfSpec;
		
	}
	
	public static DistributionManagement readDistributionManagementFromPom(Model pomModel, File pom) 
			throws Exception {
		
		DistributionManagement dm = pomModel.getDistributionManagement();
		int i = 0; 
		while( dm == null && i<10) {
			String rp = pomModel.getParent().getRelativePath();
			pom = new File(pom.getParent() + "/" + rp + "/pom.xml");
			pomModel = VPDMfGeneratorConverters.readModelFromPom(pom);
			dm = pomModel.getDistributionManagement();
			i++; // safety if DM never mentioned.
		}
		
		if(dm == null) {
			throw new Exception("Can't find distribution management information");
		}
		
		return dm;
		
	}
	
}
