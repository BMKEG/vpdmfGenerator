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
import edu.isi.bmkeg.utils.Converters;
import edu.isi.bmkeg.utils.springContext.AppContext;
import edu.isi.bmkeg.utils.springContext.BmkegProperties;
import edu.isi.bmkeg.vpdmf.controller.VPDMfKnowledgeBaseBuilder;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={ "/edu/isi/bmkeg/vpdmf/appCtx-VPDMfTest.xml"})
public class VpdmfGeneratorTest {
	
	ApplicationContext ctx;

	BuildVpdmfMysqlMavenZip p;
	
	String login, password, dbUrl;

	File pomFile, srcDir, buildDir;
	
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
	
		pomFile = ctx.getResource(
				"classpath:edu/isi/bmkeg/vpdmf/people/pom.xml"
				).getFile();
		srcDir = pomFile.getParentFile();
		buildDir = new File( srcDir.getPath() + "/target");
		
	}

	@After
	public void tearDown() throws Exception {
	//	Converters.recursivelyDeleteFiles(buildDir);
	}

	@Test
	public final void testBuildVpdmfMysqlMavenZip() throws Exception {
				
		String[] args = new String[] { 
				srcDir.getPath(),
				buildDir.getPath(),
				"1.1.5-SNAPSHOT"
				};
		
		BuildVpdmfMysqlMavenZip.main(args);
				
	}

	@Test
	public final void testBuildVpdmfModelMavenProject() throws Exception {
				
		String[] args = new String[] { 
				srcDir.getPath(),
				buildDir.getPath(),
				"1.1.5-SNAPSHOT"
				};
				
		BuildVpdmfModelMavenProject.main(args);
				
	}
	
	@Test
	public final void testBuildVpdmfServicesMavenProject() throws Exception {
				
		String[] args = new String[] { 
				srcDir.getPath(),
				buildDir.getPath(),
				"1.1.5-SNAPSHOT"
				};
				
		BuildVpdmfServicesMavenProject.main(args);
				
	}
	
}

