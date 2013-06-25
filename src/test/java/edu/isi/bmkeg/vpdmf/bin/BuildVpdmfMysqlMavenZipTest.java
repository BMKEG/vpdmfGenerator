package edu.isi.bmkeg.vpdmf.bin;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import edu.isi.bmkeg.uml.model.UMLmodel;
import edu.isi.bmkeg.utils.springContext.AppContext;
import edu.isi.bmkeg.utils.springContext.BmkegProperties;
import edu.isi.bmkeg.vpdmf.controller.VPDMfKnowledgeBaseBuilder;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={ "/edu/isi/bmkeg/vpdmf/appCtx-VPDMfTest.xml"})
public class BuildVpdmfMysqlMavenZipTest {
	
	ApplicationContext ctx;

	BuildVpdmfMysqlMavenZip p;
	
	String login, password, dbUrl;
	File ooevvSpecFile, kefedSpecFile;
	File ftdSpecFile, peopleSpecFile, digLibSpecFile, triageSpecFile;
//	File zipFile;
	
	@Before
	public void setUp() throws Exception {
		
		ctx = AppContext.getApplicationContext();
		BmkegProperties prop = (BmkegProperties) ctx.getBean("bmkegProperties");

		login = prop.getDbUser();
		password = prop.getDbPassword();
		dbUrl = prop.getDbUrl();
		
		int l = dbUrl.lastIndexOf("/");
		if (l != -1)
			dbUrl = dbUrl.substring(l + 1, dbUrl.length());
	
//		ooevvSpecFile = ctx.getResource(
//				"classpath:edu/isi/bmkeg/vpdmf/ooevv/ooevv_vpdmf.xml").getFile();

//		kefedSpecFile = ctx.getResource(
//				"classpath:edu/isi/bmkeg/vpdmf/kefed/kefed_vpdmf.xml").getFile();

		ftdSpecFile = ctx.getResource(
				"classpath:edu/isi/bmkeg/vpdmf/ftd/ftd_vpdmfSpec.xml").getFile();

		digLibSpecFile = ctx.getResource(
				"classpath:edu/isi/bmkeg/vpdmf/digitalLibrary/digitalLibrary_vpdmfSpec.xml").getFile();

		peopleSpecFile = ctx.getResource(
				"classpath:edu/isi/bmkeg/vpdmf/people/people_vpdmfSpec.xml").getFile();

		triageSpecFile = ctx.getResource(
				"classpath:edu/isi/bmkeg/vpdmf/triage/triage_vpdmfSpec.xml").getFile();

//		zipFile = new File("target/tempVpdmf.zip");
		
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testBuildFtdArchive() throws Exception {
				
		String[] args = new String[] { 
				ftdSpecFile.getParent(),
				"target",
				"1.1.3-SNAPSHOT"
				};
		
		BuildVpdmfMysqlMavenZip.main(args);
				
	}

	@Test
	public final void testBuildDigLibArchive() throws Exception {
				
		String[] args = new String[] { 
				digLibSpecFile.getParent(),
				ftdSpecFile.getParent(),
				peopleSpecFile.getParent(),
				"target",
				"1.1.3-SNAPSHOT"
				};
		
		BuildVpdmfMysqlMavenZip.main(args);
				
	}

	@Test
	public final void testBuildTriageArchive() throws Exception {
				
		String[] args = new String[] { 
				triageSpecFile.getParent(),
				digLibSpecFile.getParent(),
				ftdSpecFile.getParent(),
				peopleSpecFile.getParent(),
				"target",
				"1.1.3-SNAPSHOT"
				};
		
		BuildVpdmfMysqlMavenZip.main(args);
				
	}
		
}

