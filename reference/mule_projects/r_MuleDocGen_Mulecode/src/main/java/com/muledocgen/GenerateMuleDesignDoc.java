package com.muledocgen;

import java.awt.image.BufferedImage;
import java.io.File;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowAlign;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFAbstractSDT;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRelation;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class GenerateMuleDesignDoc {

	static XWPFParagraph new_par;
	static XWPFParagraph new_par5;
	static String projectname = null;
	static String[] files;
	static int proccesscount = 0;
	static int activitiescount = 0;
	static int gvcount = 0;
	static int sharedconncount = 0;
	static ArrayList<String> filelist = new ArrayList<String>();
	//static ArrayList<String> processlist = new ArrayList<String>();
	//static ArrayList<String> processlist = null;
	static ReadProperties rp = new ReadProperties();
	static String infilesize = null;

	public static final String TEXT = "#text";
	public static final String Headers = "Headers";
	public static final String NameValuePair = "NameValuePair";
	public static final String NameValuePairs = "NameValuePairs";
	public static final String NameValuePairPassword = "NameValuePairPassword";
	public static final String inputBindings = "pd:inputBindings";
	public static final String starter = "pd:starter";
	public static final String activity = "pd:activity";
	public static final String config = "config";
	public static final String variable = "xsl:variable";
	
	public static String outstring = "failed";
	
	public static String logo = "logo-leaf.png";
	
	public static String getConfigFiles() {
		
		InputStream inputStream;
		
		return "";
	}
	
	static XWPFHyperlinkRun createHyperlinkRun(XWPFParagraph paragraph, String uri) {
		  String rId = paragraph.getDocument().getPackagePart().addExternalRelationship(
		    uri, 
		    XWPFRelation.HYPERLINK.getRelation()
		   ).getId();

		  CTHyperlink cthyperLink=paragraph.getCTP().addNewHyperlink();
		  cthyperLink.setId(rId);
		  cthyperLink.addNewR();

		  return new XWPFHyperlinkRun(
		    cthyperLink,
		    cthyperLink.getRArray(0),
		    paragraph
		   );
		 }
	
	public static final String prettyPrint(Document xml) throws Exception {
		
		System.out.println("prettyPrint -- in Method");
		Transformer tf = TransformerFactory.newInstance().newTransformer();
		tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		tf.setOutputProperty(OutputKeys.INDENT, "yes");
		Writer out = new StringWriter();
		tf.transform(new DOMSource(xml), new StreamResult(out));
		System.out.println(out.toString());
		return out.toString();
		
	}
	
	public static void getChildNodes(Node node, XWPFDocument xdoc, XmlCursor cursor, String tocCounter, int childCount, String heading ) throws InvalidFormatException {
		
		String flowName = "";
		if( node.getNodeName().equals("flow") || node.getNodeName().equals("sub-flow") ) {
			NamedNodeMap map = node.getAttributes();
			Node attributeNode = map.getNamedItem("name");
			flowName = attributeNode.getNodeValue();
			flowName = flowName.toString();
		}
		
		cursor = new_par.getCTP().newCursor();
		
		System.out.println("Child node : " + node.getNodeName());
		//int childCount = 1;
		XWPFParagraph new_par1 = xdoc.insertNewParagraph(cursor);
		//new_par1.setStyle("Heading2");
		new_par1.setStyle(heading);
		XWPFRun titleRun = new_par1.createRun();
		//titleRun.addBreak();
		//titleRun.setText("2." + x + "." + childCount +" "+ node.getNodeName());
		//int depth=2;
		//double depthPercentage = 1/Math.sqrt(depth);
		
		childCount = childCount+1;
		String elementName = node.getNodeName();
		if( node.getNodeName().equals("flow") || node.getNodeName().equals("sub-flow") ) {
			elementName = elementName+ " - "+flowName;
			
		}
		//titleRun.setText(tocCounter+"." + childCount +" "+ node.getNodeName());
		titleRun.setText(tocCounter+"." + childCount +" "+ elementName);
		compSecPara(titleRun);	
		 
		cursor = new_par1.getCTP().newCursor();
		
		if( node.getNodeName().equals("flow") || node.getNodeName().equals("sub-flow") ) {
			
			XWPFParagraph new_par10 = xdoc.insertNewParagraph(cursor);
			//new_par10.setPageBreak(true);
			XWPFRun titleRun10 = new_par10.createRun();
			//titleRun10.addBreak();
			
			cursor = new_par.getCTP().newCursor();
			
			
			
			//Path imagePath = Paths.get(ClassLoader.getSystemResource(logo).toURI());
			//Path imagePath = Paths.get(ClassLoader.getSystemResource(processlist.get(i)).toURI());
			try {
				//String flowDiagramImages = rp.property().getProperty("mule.flowdiagrams.location").toString();
				String mulehome = System.getenv("MULE_HOME").toString().replace(System.getProperty("file.separator"), "/");
				String appname = "mule_designdoc_gen";
				String flowDiagramImages = mulehome +"/apps/"+ appname + "/WorkingDirectory/Images/";
				flowDiagramImages= flowDiagramImages+"img"+System.getProperty("file.separator");
				flowDiagramImages= flowDiagramImages.toString().replace(System.getProperty("file.separator"), "/");
				System.out.println("Image location : "+flowDiagramImages);
				System.out.println("Flow Name : "+flowName);
				//String prjpath = filelist.toArray()[r].toString().replace(System.getProperty("file.separator"), "/");
				Path imagePath = Paths.get(flowDiagramImages+System.getProperty("file.separator")+flowName+".png");
				System.out.println("Image Path : "+imagePath);
				
				BufferedImage bimg = ImageIO.read(new File(flowDiagramImages+System.getProperty("file.separator")+flowName+".png"));
				int width          = bimg.getWidth();
				int height         = bimg.getHeight();
				System.out.println("Image Width : "+width);
				System.out.println("Image height : "+height);
				

				
				//XmlCursor cursor9_image1 = new_par.getCTP().newCursor();
				XWPFParagraph image1 = xdoc.insertNewParagraph(cursor);
				//XWPFParagraph image = document.createParagraph();
				image1.setAlignment(ParagraphAlignment.CENTER);
				XWPFRun imageRun = image1.createRun();
				imageRun.setTextPosition(20);
				
				if (width<1000)
			     {
					imageRun.addPicture(Files.newInputStream(imagePath), XWPFDocument.PICTURE_TYPE_PNG,
							imagePath.getFileName().toString(), Units.toEMU(width/2), Units.toEMU(height/2));
			     }
				else
			     {
				imageRun.addPicture(Files.newInputStream(imagePath), XWPFDocument.PICTURE_TYPE_PNG,
						imagePath.getFileName().toString(), Units.toEMU(width/5), Units.toEMU(height/5));
			     }
				
				cursor = new_par.getCTP().newCursor();
				
			}catch (IOException e) {
				
				// TODO: handle exception
				System.out.println(e.getMessage());
			}			
		}
		
		
		String tocCounterString = tocCounter+"." + childCount;
		// get all the attributes of the node
		XmlCursor cursorTable = new_par.getCTP().newCursor();
				
		getNodeAttributes(node, xdoc, cursorTable);
		//String nextlevel = (childCount+
		// Get all employees
		NodeList nChildList = node.getChildNodes();
		//int childCount1 = 1;
		//childCount = childCount+0.1f;
		int counterEx = 0;
		for (int i = 0; i < nChildList.getLength(); i++) {
			
			childCount = i;
			/*
			if(i==0) {
				
				childCount=0;
			}else {
				
				childCount++;
			}
			*/
			if (nChildList.item(i).getNodeType() == Node.ELEMENT_NODE) {
				
				XmlCursor cursorChild = new_par.getCTP().newCursor();
				//counterEx
				getChildNodes(nChildList.item(i),xdoc, cursorChild, tocCounterString,counterEx,"Heading"+2);
				//childCount = childCount+1;
				counterEx++;
			} else {
				XmlCursor cursorChild = new_par.getCTP().newCursor();
				// 4 is CDATA
				//if (nChildList.item(i).getNodeType() == 4 || nChildList.item(i).getNodeType() == 3) {
				if (nChildList.item(i).getNodeType() == 3 && nChildList.item(i).getNodeValue().trim().length()>0) {
					System.out.println("Node Value : " + nChildList.item(i).getNodeValue());
					
					XmlCursor cursorChild1 = new_par.getCTP().newCursor();
					XWPFTable tableOne = xdoc.insertNewTbl(cursorChild1);
					tableOne.setWidth("95%");
					tableOne.setTableAlignment(TableRowAlign.CENTER);
					XWPFTableRow tableOneRowOne2 = tableOne.getRow(0);
					tableOneRowOne2.getCell(0).setColor("c6e5f5");
					tableOneRowOne2.getCell(0).setText(nChildList.item(i).getNodeValue());	
					
					XmlCursor cursorTable1 = new_par.getCTP().newCursor();
					XWPFParagraph new_par11 = xdoc.insertNewParagraph(cursorTable1);
					XWPFRun titleRun1 = new_par11.createRun();
					titleRun1.addBreak();
					
					counterEx++;
				}				
			}
		}
		
		childCount = childCount+1;
	}

	public static void getNodeAttributes(Node node, XWPFDocument xdoc, XmlCursor cursor) {
		
		NamedNodeMap map = node.getAttributes();
		if(map.getLength() != 0) {
			
			XWPFTable tableOne = xdoc.insertNewTbl(cursor);
			tableOne.setWidth("95%");
			tableOne.setTableAlignment(TableRowAlign.CENTER);
			XWPFTableRow tableOneRowOne2 = tableOne.getRow(0);
			compSecPara_rowDetails(tableOneRowOne2);
			
			//NamedNodeMap map = node.getAttributes();
			for (int i = 0; i < map.getLength(); i++) {
				Node attributeNode = map.item(i);
				System.out.println("         " + node.getNodeName() + " -- " + attributeNode.getNodeName() + " -- "
						+ attributeNode.getNodeValue());
				
				XWPFTableRow row2 = tableOne.createRow();
				row2.getCell(0).setText(attributeNode.getNodeName());
				//row2.getCell(1).setText(node2.getTextContent());
				row2.getCell(1).setText(attributeNode.getNodeValue());
			}
			
			XmlCursor cursorTable = new_par.getCTP().newCursor();
			
			XWPFParagraph new_par1 = xdoc.insertNewParagraph(cursorTable);
			//new_par1.setStyle("Heading2");
			//new_par1.setStyle(heading);
			XWPFRun titleRun = new_par1.createRun();
			titleRun.addBreak();
		}		
	}
	public static void main(String[] args) throws FileNotFoundException, IOException {
		
	}
	public String  Gendoc(String mulehome,String apphome,String infilename,String outfilename,String inputLocation,String outputLocation,String serviceName) throws FileNotFoundException, IOException {

		XWPFParagraph componentSectionParagraph = null;
		XWPFParagraph globalSectionParagraph = null;
		XWPFParagraph sharedSectionParagraph = null;
		XWPFParagraph referSectionParagraph = null;
		XWPFParagraph introSectionParagraph = null;
		
		ArrayList<String> processlist = null;
		
		XWPFParagraph flowDiagramParagraph = null;		
		boolean isGlobalConfigDefined = false;
		int tocCounter = 1;
		float increment =0.1f;
		
		//processlist = new ArrayList<String>();
		boolean firstDisplay = false;

		try {
			String infolder = inputLocation.toString().replace(System.getProperty("file.separator"), "/");
			//String infolder = rp.property().getProperty("mule.zipinfolder").toString().replace(System.getProperty("file.separator"), "/");
			
			String outfolder = outputLocation + "/" ;
			//String outfolder = rp.property().getProperty("outfolder").toString().replace(System.getProperty("file.separator"), "/");
			//String templatepath = mulehome+"/apps/"+apphome+"/classes/template/TDD_template.docx";
			
			String templatepath = mulehome+"/apps/"+apphome+"/properties/classes/template/"+rp.property().getProperty("mule.templatepath.name").toString();
			//String templatepath = mulehome+"/apps/"+apphome+"/classes/template/TDD_template_mule_V2.docx";
			
			
			//String templatepath = rp.property().getProperty("mule.templatepath").toString().replace(System.getProperty("file.separator"), "/");
			FileInputStream fis = null;
			filelist = new ArrayList<String>();
			//filelist = FileUtil.listFilesAndFilesSubDirectories(infolder, ".*zip");
			filelist = FileUtil.listFilesAndFilesSubDirectories(infolder,infilename);
			
			if ((filelist.size()) != 0) {
				for (int r = 0; r < filelist.size(); r++) {
					if (filelist.toArray()[r].toString().contains(System.getProperty("file.separator"))) {
						projectname = new String();
						proccesscount = 0;
						activitiescount = 0;
						gvcount = 0;
						sharedconncount = 0;
						infilesize = null;
						Date dateobj = new Date();

						//String prjpath = filelist.toArray()[r].toString().replace("\\", "/");
						String prjpath = filelist.toArray()[r].toString().replace(System.getProperty("file.separator"), "/");
						String[] splittedprjName = prjpath.split("/");
						String prjname = splittedprjName[splittedprjName.length - 1];
						projectname = serviceName;
						
						File file = new File(infolder + projectname + ".zip");
						double bytes = file.length();
						double kilobytes = (bytes / 1024);
						double megabytes = Math.round((kilobytes / 1024) * 100.0) / 100.0;
						infilesize = megabytes + " MB";
						//no need to unzip the file as the file is already in .zip format
						//FileUtil.rename(infolder + projectname + ".ear", infolder + projectname + ".zip");
						String zipFilePath = infolder + projectname + ".zip";
						//String destDir = infolder + projectname;
						String destDir = infolder;
						String projectnameUnzip = null;
						//projectname = FileUtil.unzip(zipFilePath, destDir);
						FileUtil.unzip(zipFilePath, destDir);
						//projectname = projectname
						//projectname = serviceName;
						
						
						String zippath = infolder + projectname + System.getProperty("file.separator");
						
						firstDisplay = false;
						tocCounter =1;
						isGlobalConfigDefined = false;

						/*						
							try {
								FileUtil.rename(zippath + "Process Archive.par", zippath + "Process Archive.zip");
								FileUtil.rename(zippath + "Shared Archive.sar", zippath + "Shared Archive.zip");
							} catch (FileNotFoundException ex) {
								throw ex;
							}
	
							FileUtil.unzip(zippath + "Process Archive.zip", zippath + "process");
							FileUtil.unzip(zippath + "Shared Archive.zip", zippath + "shared");							
						*/
						
						fis = new FileInputStream(templatepath);
						XWPFDocument xdoc = new XWPFDocument(fis);

						@SuppressWarnings("rawtypes")
						Iterator iterator = xdoc.getBodyElementsIterator();
						List<XWPFAbstractSDT> sdts = new ArrayList<XWPFAbstractSDT>();

						for (@SuppressWarnings("rawtypes")
						Iterator iterator2 = iterator; iterator2.hasNext();) {
							IBodyElement e = (IBodyElement) iterator2.next();

							if (e instanceof XWPFSDT) {
								XWPFSDT sdt = (XWPFSDT) e;
								sdts.add(sdt);
							} else if (e instanceof XWPFParagraph) {
								XWPFParagraph p = (XWPFParagraph) e;
								if (p.getText().equals(rp.property().getProperty("mule.Components.section").toString())) {
									componentSectionParagraph = p;
								}
								else if (p.getText().equals(rp.property().getProperty("mule.flowdiagrams.section").toString())) {
									flowDiagramParagraph = p;
								}
								else if (p.getText().equals(rp.property().getProperty("mule.References.section").toString())) {
									referSectionParagraph = p;
								}
								else if (p.getText().equals(rp.property().getProperty("mule.introduction.section").toString())) {
									introSectionParagraph = p;
								}
								//mule.introduction.section
							}
						}
						
						FileUtil.filelist = new ArrayList<String>();
						//filelist = FileUtil.listFilesAndFilesSubDirectories(infolder, infilename);
						
						//processlist = new ArrayList<String>();
						//.replace(System.getProperty("file.separator"), "/")
						//String muleDeployPropertiesFile = rp.property().getProperty("mule.app.location.deployFileName").toString();
						//String muleFlowFilelocation=rp.property().getProperty("mule.app.location").toString();
						
						String muleDeployPropertiesFile = rp.property().getProperty("mule.app.location.deployFileName").toString().replace(System.getProperty("file.separator"), "/");
						String muleFlowFilelocation=rp.property().getProperty("mule.app.location").toString().replace(System.getProperty("file.separator"), "/");
						
						//String effectivePath = infolder + projectname + "/"+muleDeployPropertiesFile;
						String effectivePath = infolder + projectname + System.getProperty("file.separator")+muleDeployPropertiesFile;
						// get the property value and print it out
						Properties prop = new Properties();
						InputStream inputStream;
						inputStream=new FileInputStream(effectivePath);
						//prop.load(new FileInputStream(effectivePath));
						prop.load(inputStream);
						String muleConfigFiles = prop.getProperty("config.resources");
					
						inputStream.close();
						System.out.println("muleConfigFiles : " + muleConfigFiles);
						StringTokenizer stringTokenizer = new StringTokenizer(muleConfigFiles, ",");
						
						String muleConfigFile = "";
						processlist = new ArrayList<String>();
						List nodesList = new ArrayList<Node>();
						while (stringTokenizer.hasMoreTokens()) {
							
							muleConfigFile =stringTokenizer.nextToken();
							System.out.println("muleConfigFile token: " + muleConfigFile);
							System.out.println("config file with path : "+infolder + projectname + System.getProperty("file.separator")+muleFlowFilelocation+ muleConfigFile);
							processlist.add(infolder + projectname + System.getProperty("file.separator") + muleFlowFilelocation + muleConfigFile);
							
						}
						
						if (componentSectionParagraph != null) {
							tocCounter = tocCounter+1;
							for (int x = 0; x < processlist.size(); x++) {
								XmlCursor cursor;
								if (x == 0) {
									cursor = componentSectionParagraph.getCTP().newCursor();
									cursor.toNextSibling();
									new_par = xdoc.insertNewParagraph(cursor);
								} else {
									cursor = new_par.getCTP().newCursor();
									new_par = xdoc.insertNewParagraph(cursor);
								}

								org.w3c.dom.Document xmldocument;
								DocumentBuilder builder = readDoc();
								xmldocument = builder.parse(new File((String) processlist.toArray()[x]));
								((org.w3c.dom.Document) xmldocument).getDocumentElement().normalize();
								//XmlCursor cursor9 = new_par.getCTP().newCursor();
								//XWPFParagraph new_par9 = xdoc.insertNewParagraph(cursor9);
								//new_par9.setStyle("Heading1");
								
								//XWPFRun titleRun9 = new_par9.createRun();
								//titleRun9.setBold(true);
								//compSecPara_Format(titleRun9);
								//String filepath = processlist.toArray()[x].toString().replace("\\", "/");
								String filepath = processlist.toArray()[x].toString().replace(System.getProperty("file.separator"), "/");
								
								System.out.println(filepath);
								//String[] splittedprocesspath = filepath.split("/process/");
								String[] splittedprocesspath = filepath.split("/process/");
								String processpath = splittedprocesspath[splittedprocesspath.length - 1];
								//String[] splittedFileName = filepath.split("/");
								String[] splittedFileName = filepath.split("/");
								String processname = splittedFileName[splittedFileName.length - 1];
								//titleRun9.setText(tocCounter+"." + (x + 1) + "." + processname);

								NodeList nChildList1 = xmldocument.getChildNodes();
								NodeList nChildList = nChildList1.item(0).getChildNodes();
								
								boolean displayPath = false;
								for (int i = 0; i < nChildList.getLength(); i++) {				
									
									Node node = nChildList.item(i);
									if (node.getNodeType() == Node.ELEMENT_NODE) {
										if (node.getParentNode().getNodeName().equals("mule") ) {
											if(node.getNodeName().equals("flow") || node.getNodeName().equals("sub-flow")){
												displayPath = true;
												break;
											}
										}
									}
								}

								if(displayPath) {
									
									XmlCursor cursor10 = new_par.getCTP().newCursor();
									XWPFParagraph new_par10 = xdoc.insertNewParagraph(cursor10);
									
									if(firstDisplay) {
										new_par10.setPageBreak(true);
									}
									firstDisplay = true;
									
									XWPFRun titleRun10 = new_par10.createRun();
									titleRun10.addBreak();
									titleRun10.setFontSize(14);
									int beginIndex = processpath.indexOf("/src/");
									titleRun10.setText("Process file path: " + processpath.substring(beginIndex));
								}
								
								
								XmlCursor cursor11 = null;
								int count = 1;
								int childCount =1;
								
								for (int i = 0; i < nChildList.getLength(); i++) {
									if(i ==0 ){
										childCount = 0;
									}else {
										
									}
									cursor11 =new_par.getCTP().newCursor();
									Node node = nChildList.item(i);
									if (node.getNodeType() == Node.ELEMENT_NODE) {
										if (node.getParentNode().getNodeName().equals("mule") ) {
											if(node.getNodeName().equals("flow") || node.getNodeName().equals("sub-flow")){
												
												proccesscount++;
												
												if (x == 0) {
													//tocCounter =2; // this is for Component
												}
												//getChildNodes(node,xdoc,cursor11, String.valueOf(2),childCount);
												getChildNodes(node,xdoc,cursor11, String.valueOf(tocCounter),x,"Heading"+1);
												childCount= childCount+1;
											}else {
												//adding Global Elements
												nodesList.add(node);
												sharedconncount++;
											}
										}																					
									}									
								}
								
								if(x == processlist.size()-1) {
									//childCount =0;
									tocCounter = 3;
									//String tocCounterString = tocCounter+"." + 0 ;
									String tocCounterString = String.valueOf(tocCounter) ;
									if(!isGlobalConfigDefined) {
										
										XmlCursor cursor_s = new_par.getCTP().newCursor();
										XWPFParagraph new_par10_s = xdoc.insertNewParagraph(cursor_s);
										new_par10_s.setStyle("Heading1");
										XWPFRun titleRun10_s = new_par10_s.createRun();
										titleRun10_s.setBold(true);
										compSecPara_Format(titleRun10_s);	
										
										titleRun10_s.setText(tocCounterString + " " + "Global Configuration");
										
										isGlobalConfigDefined = true;
										
									}		
									
									tocCounterString = String.valueOf(tocCounter);
									childCount = 0;
									for (int j = 0; j < nodesList.size(); j++) {
										if(j == 0) {
											childCount = 0;
										}else {
											childCount++;
										}
										//childCount= childCount+1;	
										//Global Elements
										XmlCursor cursor_s1 = new_par.getCTP().newCursor();
										Node node =(Node)nodesList.get(j);
										getChildNodes(node,xdoc,cursor_s1, tocCounterString,childCount,"Heading"+2);
																		
									}
								}
								
								
							}
						}
						
						if(flowDiagramParagraph != null) {
							XmlCursor cursor;
							cursor = flowDiagramParagraph.getCTP().newCursor();
							cursor.toNextSibling();
							new_par = xdoc.insertNewParagraph(cursor);
							//System.getProperty("file.separator")
							//String flowDiagramImages = rp.property().getProperty("mule.flowdiagrams.location").toString();
							String flowDiagramImages = rp.property().getProperty("mule.flowdiagrams.location").toString().replace(System.getProperty("file.separator"), "/");;
							flowDiagramImages= flowDiagramImages+projectname+System.getProperty("file.separator") + rp.property().getProperty("mule.flowdiagrams.location.imagefolder").toString();
							System.out.println("Image location : "+flowDiagramImages);
							XmlCursor cursor9 = new_par.getCTP().newCursor();
							XWPFParagraph new_par9 = xdoc.insertNewParagraph(cursor9);
							new_par9.setStyle("Heading1");
							XWPFRun titleRun9 = new_par9.createRun();
							titleRun9.setBold(true);
							compSecPara_Format(titleRun9);
							
							titleRun9.setText("2." + 1 + "." + muleConfigFile+ " Application Snapshot");

							XmlCursor cursor9_image1 = new_par.getCTP().newCursor();
							XWPFParagraph image1 = xdoc.insertNewParagraph(cursor9_image1);
							//XWPFParagraph image = document.createParagraph();
							image1.setAlignment(ParagraphAlignment.CENTER);
							XWPFRun imageRun = image1.createRun();
							imageRun.setTextPosition(20);
							try {
								int indexofXML = muleConfigFile.indexOf(".xml");
								String flowSnapShotImage = muleConfigFile.substring(0, indexofXML);
								
								//Path imagePath = Paths.get(flowDiagramImages+"\\"+flowSnapShotImage+".png");
								Path imagePath = Paths.get(flowDiagramImages+System.getProperty("file.separator")+flowSnapShotImage+".png");
								
								imageRun.addPicture(Files.newInputStream(imagePath), XWPFDocument.PICTURE_TYPE_PNG,
										imagePath.getFileName().toString(), Units.toEMU(400), Units.toEMU(300));
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						if(referSectionParagraph != null) {
							XmlCursor cursor;
							cursor = referSectionParagraph.getCTP().newCursor();
							cursor.toNextSibling();
							new_par = xdoc.insertNewParagraph(cursor);
							
							//XWPFParagraph paragraph = document.createParagraph();
							XWPFRun run = new_par.createRun();
							run.setText("1. Refer to document ");

						  	XWPFHyperlinkRun hyperlinkrun = createHyperlinkRun(new_par, "https://www.ibm.com");
						  	hyperlinkrun.setText("API/Mulesoft best practices");
							hyperlinkrun.setColor("0000FF");
							hyperlinkrun.setUnderline(UnderlinePatterns.SINGLE);

						  	run = new_par.createRun();
						  	run.setText(" at API/C4E site.");
						  
						  	// ---- second link --------
						  	cursor = new_par.getCTP().newCursor();
						  	cursor.toNextSibling();
							//cursor.toNextSibling();
							new_par = xdoc.insertNewParagraph(cursor);
							
							//XWPFParagraph paragraph = document.createParagraph();
							XWPFRun run1 = new_par.createRun();
							run1.setText("2. Refer to  ");

							XWPFHyperlinkRun hyperlinkrun1 = createHyperlinkRun(new_par, "https://www.ibm.com");
							hyperlinkrun1.setText("Security/Encryption for 3rd party service integration");
							hyperlinkrun1.setColor("0000FF");
							hyperlinkrun1.setUnderline(UnderlinePatterns.SINGLE);

							run1 = new_par.createRun();
							run1.setText(".");
						}

						if (sharedSectionParagraph != null) {
							FileUtil.filelist = new ArrayList<String>();
							ArrayList<String> filelistq = FileUtil
									.listFilesAndFilesSubDirectories(infolder + projectname, ".*.shared.*");

							for (int x = 0; x < filelistq.size(); x++) {
								XmlCursor cursor;
								if (x == 0) {
									cursor = sharedSectionParagraph.getCTP().newCursor();
									cursor.toNextSibling();
									new_par = xdoc.insertNewParagraph(cursor);
								} else {
									cursor = new_par.getCTP().newCursor();
									new_par = xdoc.insertNewParagraph(cursor);
								}

								org.w3c.dom.Document xmldocument;
								DocumentBuilder builder = readDoc();
								xmldocument = builder.parse(new File((String) filelistq.toArray()[x]));
								((org.w3c.dom.Document) xmldocument).getDocumentElement().normalize();
								XmlCursor cursor9 = new_par.getCTP().newCursor();
								XWPFParagraph new_par9 = xdoc.insertNewParagraph(cursor9);
								new_par9.setStyle("Heading1");
								XWPFRun titleRun9 = new_par9.createRun();
								compSecPara_Format(titleRun9);
								//String filepath = filelistq.toArray()[x].toString().replace("\\", "/");
								String filepath = filelistq.toArray()[x].toString().replace(System.getProperty("file.separator"), "/");
								
								String[] splittedFileName = filepath.split("/");
								String processname = splittedFileName[splittedFileName.length - 1];

								titleRun9.setText("4." + (x + 1) + "." + processname);
								NodeList nChildList1 = xmldocument.getChildNodes();
								NodeList nChildList = nChildList1.item(0).getChildNodes();

								for (int j = 0; j < nChildList.getLength(); j++) {
									Node node1 = nChildList.item(j);
									if ((node1.getNodeName()).equals(config)) {
										XmlCursor cursor6 = new_par.getCTP().newCursor();
										XWPFTable tableOne = xdoc.insertNewTbl(cursor6);
										tableOne.setWidth("100%");
										XWPFTableRow tableOneRowOne2 = tableOne.getRow(0);
										compSecPara_rowDetails(tableOneRowOne2);
										if (node1.hasChildNodes()) {
											NodeList nChildListb = node1.getChildNodes();
											for (int k = 0; k < nChildListb.getLength(); k++) {
												Node node2 = nChildListb.item(k);
												if (!node2.getNodeName().equals(TEXT)
														&& !(node2.getNodeName()).equals(Headers)) {
													XWPFTableRow row2 = tableOne.createRow();
													row2.getCell(0).setText(node2.getNodeName());
													row2.getCell(1).setText(node2.getTextContent());
												}
											}
										}
									}
								}sharedconncount++;
							}
						}

						if (globalSectionParagraph != null) {
							XmlCursor cursor;
							cursor = globalSectionParagraph.getCTP().newCursor();
							cursor.toNextSibling();
							new_par = xdoc.insertNewParagraph(cursor);

							org.w3c.dom.Document xmldocument;
							DocumentBuilder builder = readDoc();
							xmldocument = builder.parse(new File(infolder + projectname + System.getProperty("file.separator")+"TIBCO.xml"));
							((org.w3c.dom.Document) xmldocument).getDocumentElement().normalize();

							NodeList nChildList1 = xmldocument.getChildNodes();
							NodeList nChildList = nChildList1.item(0).getChildNodes();

							XmlCursor cursor6 = new_par.getCTP().newCursor();
							XWPFTable tableOne = xdoc.insertNewTbl(cursor6);
							tableOne.setWidth("100%");
							XWPFTableRow tableOneRowOne2 = tableOne.getRow(0);
							tableOneRowOne2.getCell(0).setText("Element Name");
							tableOneRowOne2.addNewTableCell().setText("Element Value");
							introSecPara_Format(tableOneRowOne2);
							for (int i = 0; i < nChildList.getLength(); i++) {
								Node node = nChildList.item(i);
								if ((node.getNodeName()).equals(NameValuePairs)) {
									if (node.hasChildNodes()) {
										NodeList nChildListb = node.getChildNodes();
										for (int k = 0; k < nChildListb.getLength(); k++) {
											Node node2 = nChildListb.item(k);
											if ((node2.getNodeName()).equals(NameValuePair)
													|| (node2.getNodeName()).equals(NameValuePairPassword)
															&& (!(node2.getNodeName()).equals(TEXT))) {
												NodeList nChildListc = node2.getChildNodes();
												XWPFTableRow row2 = tableOne.createRow();
												row2.getCell(0).setText(
														nChildListc.item(1).getTextContent().replace(System.getProperty("file.separator"), "."));
												row2.getCell(1).setText(nChildListc.item(3).getTextContent());
												gvcount++;
											}
										}
									}
								}
							}
						}
						if (referSectionParagraph != null) {
							XmlCursor cursor;
							cursor = referSectionParagraph.getCTP().newCursor();
							cursor.toNextSibling();
							new_par = xdoc.insertNewParagraph(cursor);
							XWPFRun titleRun9 = new_par.createRun();
							titleRun9.addBreak();
							titleRun9.setText(rp.property().getProperty("reference").toString());
						}
						
						if (introSectionParagraph != null) {
							XmlCursor cursor;
							cursor = introSectionParagraph.getCTP().newCursor();
							cursor.toNextSibling();
							new_par = xdoc.insertNewParagraph(cursor);
							XWPFRun titleRun9 = new_par.createRun();
							titleRun9.addBreak();
							titleRun9.setText(rp.property().getProperty("introduction").toString());
							titleRun9.addBreak();
							titleRun9.setText("Project File Name: " + projectname + ".zip");
							titleRun9.addBreak();
							titleRun9.setText("File Size: " + infilesize);
							titleRun9.addBreak();
							DateFormat dx = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
							titleRun9.setText("Document created on: " + dx.format(dateobj));
							
							XmlCursor cursor3 = new_par.getCTP().newCursor();
							cursor3.toNextSibling();
							XWPFTable tableOne = xdoc.insertNewTbl(cursor3);
							tableOne.setWidth("95%");
							tableOne.setTableAlignment(TableRowAlign.CENTER);
							XWPFTableRow tableOneRowOne2 = tableOne.getRow(0);
							tableOneRowOne2.getCell(0).setText("Type");
							tableOneRowOne2.addNewTableCell().setText("Count");
							introSecPara_Format(tableOneRowOne2);
							XWPFTableRow row2 = tableOne.createRow();

							row2.getCell(0).setText("Processes/Flows");
							row2.getCell(1).setText("" + proccesscount);
							XWPFTableRow row3 = tableOne.createRow();
							row3.getCell(0).setText("Global Elements");
							row3.getCell(1).setText("" + sharedconncount);
							/*
							XWPFTableRow row4 = tableOne.createRow();
							row4.getCell(0).setText("Activities");
							row4.getCell(1).setText("" + activitiescount);
							XWPFTableRow row5 = tableOne.createRow();
							row5.getCell(0).setText("GVs");
							row5.getCell(1).setText("" + gvcount);
							*/
							
						}
						
						File directory = new File(outfolder);
					    if (! directory.exists()){
					        directory.mkdir();
					    }
						
						//DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
						//String date = df.format(dateobj);
						//FileOutputStream out = new FileOutputStream(
						//		outfolder + projectname + "_Mulesoft_Design_Document_" + date + ".docx");
						FileOutputStream out = new FileOutputStream(
								outfolder + outfilename);
						xdoc.write(out);
						//xdoc.createTOC();
						//xdoc.enforceUpdateFields();
						out.close();
						xdoc.close();
						//File projectpath = new File(destDir + System.getProperty("file.separator")+projectname );
						File projectpath = new File(destDir+System.getProperty("file.separator")+projectname);
						FileUtil.deleteDirectory(projectpath);
						//FileUtil.rename(infolder + projectname + ".zip", infolder + projectname + ".ear");
//						System.out.println(
//								"Mulesoft Design Document is created at " + System.getProperty("user.dir").replace(System.getProperty("file.separator"), "/")
//										+ System.getProperty("file.separator") + outfolder + projectname + "_Design_Documnet" + date + ".docx");
						System.out.println(
								"Mule Design Document is created at " + System.getProperty("user.dir").replace(System.getProperty("file.separator"), "/")
										+ "/" + outfolder.replace(System.getProperty("file.separator"), "/") + outfilename.replace(System.getProperty("file.separator"), "/") );
						outstring = "success";
					}
				}
			} else {
				
				throw new FileNotFoundException("zip files not found in the src/main/resources/in folder");
				
			}
		} catch (Exception e) {
			
			e.printStackTrace();
			/*
				FileUtil.rename(rp.property().getProperty("earinfolder").toString() + projectname + ".zip",
					rp.property().getProperty("earinfolder").toString() + projectname + ".ear");
			*/
		}
		
		return outstring;
	}

	private static void inputBindings(Node node1, XWPFTable tableOne2) {
		if(node1.hasChildNodes()) { 
			NodeList nChildListb = node1.getChildNodes(); 
			for(int k = 0; k < nChildListb.getLength(); k++) { 
			Node node2 = nChildListb.item(k);
			if (!(node2.getNodeName()).equals(TEXT)) { 
				if(node2.getAttributes().getLength() != 0 && !(node2.getNodeName()).equals(variable) && node2.getNodeName().startsWith("xsl:")) {
					if(node2.getAttributes().item(0).getNodeName().equals("test") || (node2.getAttributes().item(0).getNodeName().equals("select") && !(node2.getNodeName().equals("xsl:value-of")))) {
							XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
							tableOneRowOne01.getCell(0).setText(node2.getNodeName());
							tableOneRowOne01.getCell(1).setText(node2.getAttributes().item(0).getNodeValue());
					}
			  	if(node2.getAttributes().getLength() == 2  && node2.getNodeName().startsWith("xsl:")) { 	
					if (node2.getAttributes().item(1).getNodeName().equals("select")) {
							XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
							tableOneRowOne01.getCell(0).setText(node2.getNodeName());
							tableOneRowOne01.getCell(1).setText(node2.getAttributes().item(1).getNodeValue());
							}
						}
					}
					if(node2.hasChildNodes()) { 
						NodeList nChildListc = node2.getChildNodes(); 
						for(int l = 0; l < nChildListc.getLength(); l++) {
							Node node3 = nChildListc.item(l); 

							if (!(node3.getNodeName()).equals(TEXT)) { 
								if(node3.getAttributes().getLength() != 0 && !(node3.getNodeName()).equals(variable) && node3.getNodeName().startsWith("xsl:")) {
									if(node3.getAttributes().item(0).getNodeName().equals("test") || (node3.getAttributes().item(0).getNodeName().equals("select") && !(node3.getNodeName().equals("xsl:value-of")))) {
										XWPFTableRow tableOneRowOne02 = tableOne2.createRow();
										tableOneRowOne02.getCell(0).setText(node2.getNodeName()+"/"+node3.getNodeName());
										tableOneRowOne02.getCell(1).setText(node3.getAttributes().item(0).getNodeValue());										
									}
									if(node3.getAttributes().getLength() == 2 && node3.getNodeName().startsWith("xsl:")) { 
										if (node3.getAttributes().item(1).getNodeName().equals("select")) {
											XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
											tableOneRowOne01.getCell(0).setText(node2.getNodeName()+"/"+node3.getNodeName());
											tableOneRowOne01.getCell(1).setText(node3.getAttributes().item(1).getNodeValue());
										}
									}
								}
								if(node3.hasChildNodes()) { 
									NodeList nChildListd = node3.getChildNodes(); 
									for(int m = 0; m < nChildListd.getLength(); m++) {
										Node node4 = nChildListd.item(m); 
										if (!(node4.getNodeName()).equals(TEXT)){ 
											if(node4.getAttributes().getLength() != 0 && !(node4.getNodeName()).equals(variable) && node4.getNodeName().startsWith("xsl:")) {
												if(node4.getAttributes().item(0).getNodeName().equals("test") || (node4.getAttributes().item(0).getNodeName().equals("select") && !(node4.getNodeName().equals("xsl:value-of")))) {
													XWPFTableRow tableOneRowOne03 = tableOne2.createRow();
													tableOneRowOne03.getCell(0).setText(node2.getNodeName()+"/"+node3.getNodeName()+"/"+node4.getNodeName());
													tableOneRowOne03.getCell(1).setText(node4.getAttributes().item(0).getNodeValue());													
												}
												if(node4.getAttributes().getLength() == 2 && node4.getNodeName().startsWith("xsl:")) { 
													if (node4.getAttributes().item(1).getNodeName().equals("select")) {
														XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
														tableOneRowOne01.getCell(0).setText(node2.getNodeName()+"/"+node3.getNodeName()+"/"+node4.getNodeName());
														tableOneRowOne01.getCell(1).setText(node4.getAttributes().item(1).getNodeValue());														
													}
												}
											}
											//node4 = nChildListd.item(m);
											if(node4.hasChildNodes()) {
												NodeList nChildListe = node4.getChildNodes();
												for(int n = 0; n < nChildListe.getLength(); n++) {
													Node node5 = nChildListe.item(n); 
													if ((!(node5.getNodeName()).equals(TEXT)) && node5.getNodeName() != null) { 
														node5 = nChildListe.item(n);
														if(node5.getAttributes().getLength() != 0 && !(node5.getNodeName()).equals(variable) && node5.getNodeName().startsWith("xsl:")) {
															if(node5.getAttributes().item(0).getNodeName().equals("test") || (node5.getAttributes().item(0).getNodeName().equals("select") && !(node5.getNodeName().equals("xsl:value-of")))) {
																XWPFTableRow tableOneRowOne03 = tableOne2.createRow();
																tableOneRowOne03.getCell(0).setText(node2.getNodeName()+"/"+node3.getNodeName()+"/"+node4.getNodeName()+"/"+node5.getNodeName());
																tableOneRowOne03.getCell(1).setText(node5.getAttributes().item(0).getNodeValue());						
															}
															if(node5.getAttributes().getLength() == 2 && node5.getNodeName().startsWith("xsl:")) { 
																if (node5.getAttributes().item(1).getNodeName().equals("select")) {
																	XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
																	tableOneRowOne01.getCell(0).setText(node2.getNodeName()+"/"+node3.getNodeName()+"/"+node4.getNodeName()+"/"+node5.getNodeName());
																	tableOneRowOne01.getCell(1).setText(node4.getAttributes().item(1).getNodeValue());														
																}
															}
														}
														if(node5.hasChildNodes()) {
															NodeList nChildListf = node5.getChildNodes();
															for(int p = 0; p < nChildListf.getLength(); p++) {
																Node node6 = nChildListf.item(p);
																if ((!(node6.getNodeName()).equals(TEXT)) && node6.getNodeName() != null) {
																	if(node6.getAttributes().getLength() != 0 && !(node6.getNodeName()).equals(variable) && node6.getNodeName().startsWith("xsl:")) {
																		if(node6.getAttributes().item(0).getNodeName().equals("test") || (node6.getAttributes().item(0).getNodeName().equals("select") && !(node6.getNodeName().equals("xsl:value-of")))) {
																			XWPFTableRow tableOneRowOne03 = tableOne2.createRow();
																			tableOneRowOne03.getCell(0).setText(node2.getNodeName()+"/"+node3.getNodeName()+"/"+node4.getNodeName()+"/"+node5.getNodeName()+"/"+node6.getNodeName());
																			tableOneRowOne03.getCell(1).setText(node6.getAttributes().item(0).getNodeValue());																			
																		}
																		if(node6.getAttributes().getLength() == 2 && node6.getNodeName().startsWith("xsl:")) { 
																			if (node6.getAttributes().item(1).getNodeName().equals("select")) {
																				XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
																				tableOneRowOne01.getCell(0).setText(node2.getNodeName()+"/"+node3.getNodeName()+"/"+node4.getNodeName()+"/"+node5.getNodeName()+"/"+node6.getNodeName());
																				tableOneRowOne01.getCell(1).setText(node6.getAttributes().item(1).getNodeValue());																				
																			}
																		}
																	} 
																	if(node6.hasChildNodes()) {
																		NodeList nChildListg = node6.getChildNodes(); 
																		for(int q = 0; q < nChildListg.getLength(); q++) {
																			Node node7 = nChildListg.item(q); 
																			if ((!(node7.getNodeName()).equals(TEXT)) && node7.getNodeName() != null) {
																				if(node7.getAttributes().getLength() != 0 && !(node7.getNodeName()).equals(variable) && node7.getNodeName().startsWith("xsl:")) {
																					if(node7.getAttributes().item(0).getNodeName().equals("test") || (node7.getAttributes().item(0).getNodeName().equals("select") && !(node7.getNodeName().equals("xsl:value-of")))) {
																						XWPFTableRow tableOneRowOne03 = tableOne2.createRow();
																						tableOneRowOne03.getCell(0).setText(node2.getNodeName()+"/"+node3.getNodeName()+"/"+node4.getNodeName()+"/"+node5.getNodeName()+"/"+node6.getNodeName()+"/"+node7.getNodeName());
																						tableOneRowOne03.getCell(1).setText(node7.getAttributes().item(0).getNodeValue());																						
																					}
																					if(node7.getAttributes().getLength() == 2 && node7.getNodeName().startsWith("xsl:")) { 
																						if (node7.getAttributes().item(1).getNodeName().equals("select")) {
																							XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
																							tableOneRowOne01.getCell(0).setText(node2.getNodeName()+"/"+node3.getNodeName()+"/"+node4.getNodeName()+"/"+node5.getNodeName()+"/"+node6.getNodeName()+"/"+node7.getNodeName());
																							tableOneRowOne01.getCell(1).setText(node7.getAttributes().item(1).getNodeValue());																							
																						}
																					}
																				} 
																				if(node7.hasAttributes() && node7.getAttributes().item(0) != null ) { 
																					XWPFTableRow row4 = tableOne2.createRow(); 
																					row4.getCell(0).setText(node2.getNodeName() +"/"+ node3.getNodeName()+"/"+ node4.getNodeName()+"/"+ node5.getNodeName()+"/"+ node6.getNodeName());
																					row4.getCell(1).setText(node7.getAttributes().item(0).getNodeValue());
																				}  
																			}
																		}}else if(node6.hasAttributes() && node6.getAttributes().item(0) != null ) { 
																				XWPFTableRow row4 = tableOne2.createRow(); 
																				row4.getCell(0).setText(node2.getNodeName() +"/"+ node3.getNodeName()+"/"+ node4.getNodeName()+"/"+ node5.getNodeName());
																				row4.getCell(1).setText(node6.getAttributes().item(0).getNodeValue());
																			}
																		}
																}															
															}else if(node5.hasAttributes() && node5.getAttributes().item(0) != null ) { 
																	XWPFTableRow row4 = tableOne2.createRow(); 
																	row4.getCell(0).setText(node2.getNodeName() +"/"+ node3.getNodeName()+"/"+ node4.getNodeName());
																	row4.getCell(1).setText(node5.getAttributes().item(0).getNodeValue());
																}
															}
														}							
											}else if(node4.hasAttributes() && node4.getAttributes().item(0) != null ) { 
													XWPFTableRow row4 = tableOne2.createRow(); 
													row4.getCell(0).setText(node2.getNodeName() +"/"+ node3.getNodeName());
													row4.getCell(1).setText(node4.getAttributes().item(0).getNodeValue());
												}
											}
										}																	
								}else if(node3.hasAttributes() && node3.getAttributes().item(0) != null ) { 
										if (node3.getNodeName().equals(variable)) {
											XWPFTableRow row3 = tableOne2.createRow(); 
											row3.getCell(0).setText(node2.getNodeName()+"/"+node3.getAttributes().item(0).getNodeValue());
											row3.getCell(1).setText(node3.getAttributes().item(1).getNodeValue());
										}else {
											XWPFTableRow row4 = tableOne2.createRow(); 
											row4.getCell(0).setText(node2.getNodeName());
											row4.getCell(1).setText(node3.getAttributes().item(0).getNodeValue());
										}
									}
								}
							} 
						} 
					else if(node2.hasAttributes() && node2.getAttributes().item(0) != null ) { 
							XWPFTableRow row2 = tableOne2.createRow(); 
							row2.getCell(0).setText(node2.getNodeName());
							row2.getCell(1).setText(node2.getAttributes().item(0).getNodeValue()); 
						}
					}
				}
			}			
		}
	
	private static void introSecPara_Format(XWPFTableRow tableOneRowOne2) {
		tableOneRowOne2.getCell(0).setColor("c6e5f5");
		tableOneRowOne2.getCell(1).setColor("c6e5f5");
	}	
	
	private static DocumentBuilder readDoc() throws ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		org.w3c.dom.Document xmldocument;
		builder = factory.newDocumentBuilder();
		return builder;
	}	
	
	private static void compSecPara(XWPFRun titleRun3) {
		titleRun3.setColor("0003CC");
		titleRun3.setFontFamily("Calibri Light (Headings)");
		titleRun3.setFontSize(16);
		titleRun3.setTextPosition(20);
	}	
	
	private static void compSecPara_Format(XWPFRun titleRun9) {
		titleRun9.setFontSize(16);
		titleRun9.setColor("0003CC");
		titleRun9.setFontFamily("Calibri Light (Headings)");
	}
	
	
	private static void compSecPara_rowDetails(XWPFTableRow tableOneRowOne2) {
		tableOneRowOne2.getCell(0).setColor("c6e5f5");
		tableOneRowOne2.getCell(0).setText("Attribute Name");
		tableOneRowOne2.addNewTableCell().setText("Attribute Value");
		tableOneRowOne2.getCell(1).setColor("c6e5f5");
	}
	
}
