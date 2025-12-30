package com.tibcodocgen;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFAbstractSDT;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
public class GenerateDesignDoc {
	static XWPFParagraph new_par;
	static XWPFParagraph new_par5;
	public String projectname = null;
	public static String prjpath= null;
	static String[] files;
	static int proccesscount = 0;
	static int activitiescount = 0;
	static int gvcount = 0;
	static int adacount =0;
	static int sharedconncount = 0;
	static int startercount = 0;
	static String strconfig = null;
	static Collection<starterobj> starterlist = new ArrayList<starterobj>();
	static ArrayList<String> filelist = null;
	static ArrayList<String> processlist = null;
	static ReadProperties rp = new ReadProperties();
	static String infilesize = null;
	public XWPFDocument xdoc = null;
	public FileOutputStream out = null;
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
	public static final String transition = "pd:transition";
	public static String transsepartor = null;
	public static String groupsepartor_start = null;
	public static String groupsepartor_end = null;
	public static String grouptransepartor = null;
	public static String transheader = null;
	public static String sharedconn_regex = null;
	public static String prjname = null;
	public static String outstring = "failed";
	public String  Gendoc(String mulehome,String apphome,String infilename,String outfilename,String EarLocation,String DocLocation) throws FileNotFoundException, IOException {
		XWPFParagraph componentSectionParagraph = null;
		XWPFParagraph globalSectionParagraph = null;
		XWPFParagraph adpterSectionParagraph = null;
		XWPFParagraph sharedSectionParagraph = null;
		XWPFParagraph referSectionParagraph = null;
		XWPFParagraph introSectionParagraph = null;
		XWPFParagraph serviceSectionParagraph = null;
		projectname = new String();
		prjpath = null;
		proccesscount = 0;
		activitiescount = 0;
		gvcount = 0;
		adacount=0;
		sharedconncount = 0;
		infilesize = null;
		prjname = null;
		starterlist = new ArrayList<starterobj>();
		Date dateobj = new Date();
		try {
			String infolder = EarLocation;
			String outfolder = DocLocation;
			String templatepath = mulehome+"/apps/"+apphome+"/properties/classes/template/TDD_template.docx";
			String processpara_num = rp.property().getProperty("processpara_num").toString();
			String sharedconnpara_num = rp.property().getProperty("sharedconnpara_num").toString();
			String adapterconnpara_num = rp.property().getProperty("adapterconnpara_num").toString();
			String straterconf = rp.property().getProperty("straterconfig").toString();
			transsepartor = rp.property().getProperty("transsepartor").toString();
			groupsepartor_start=rp.property().getProperty("groupsepartor_start").toString();
			groupsepartor_end=rp.property().getProperty("groupsepartor_end").toString();
			grouptransepartor=rp.property().getProperty("grouptransepartor").toString();
			transheader=rp.property().getProperty("transheader").toString();
			sharedconn_regex=rp.property().getProperty("sharedconn_regex").toString();
			FileInputStream fis = null;
			filelist = null;
			FileUtil.filelist = new ArrayList<String>();
			filelist = FileUtil.listFilesAndFilesSubDirectories(infolder, infilename);
			if ((filelist.size()) != 0) {
					if (filelist.toArray()[0].toString().contains(System.getProperty("file.separator"))) {
						 prjname = null;
						 prjpath = filelist.toArray()[0].toString().replace(System.getProperty("file.separator"), "/");
						String[] splittedprjName1 = prjpath.split("/");
						 prjname = splittedprjName1[splittedprjName1.length - 1];
						projectname = prjname.split(".ear")[0];
						File file = new File(infolder + projectname + ".ear");
						double bytes = file.length();
						double kilobytes = (bytes / 1024);
						double megabytes = Math.round((kilobytes / 1024) * 100.0) / 100.0;
						infilesize = megabytes + " MB";
						FileUtil.rename(infolder + projectname+".ear", infolder + projectname + ".zip");
						String zipFilePath = infolder + projectname + ".zip";
						String destDir = infolder + projectname;
						FileUtil.unzip(zipFilePath, destDir);
						String zippath = infolder + projectname + "/";
						FileUtil.filelist = new ArrayList<String>();
						 ArrayList<String> processlist = FileUtil.listFilesAndFilesSubDirectories(zippath, ".*.par");
						for (int r = 0; r < processlist.size(); r++) {
							FileUtil.rename(processlist.toArray()[r].toString(), processlist.toArray()[r].toString().split(".par")[0]+".zip");
							FileUtil.unzip(processlist.toArray()[r].toString().split(".par")[0]+".zip", zippath+((processlist.toArray()[r].toString().substring(processlist.toArray()[r].toString().lastIndexOf(System.getProperty("file.separator"))+1)).split("\\."))[0]);
						}
						FileUtil.filelist = new ArrayList<String>();
						 ArrayList<String> sharedlist = FileUtil.listFilesAndFilesSubDirectories(zippath, ".*.sar");
						for (int r = 0; r < sharedlist.size(); r++) {
							FileUtil.rename(sharedlist.toArray()[r].toString(), sharedlist.toArray()[r].toString().split(".sar")[0]+".zip");
							FileUtil.unzip(sharedlist.toArray()[r].toString().split(".sar")[0]+".zip", zippath+((sharedlist.toArray()[r].toString().substring(sharedlist.toArray()[r].toString().lastIndexOf(System.getProperty("file.separator"))+1)).split("\\."))[0]);
						}
						FileUtil.filelist = new ArrayList<String>();
						 ArrayList<String> adpterlist = FileUtil.listFilesAndFilesSubDirectories(zippath, ".*.aar");
						for (int r = 0; r < adpterlist.size(); r++) {
							FileUtil.rename(adpterlist.toArray()[r].toString(), adpterlist.toArray()[r].toString().split(".aar")[0]+".zip");
							FileUtil.unzip(adpterlist.toArray()[r].toString().split(".aar")[0]+".zip", zippath+"adapter/"+((adpterlist.toArray()[r].toString().substring(adpterlist.toArray()[r].toString().lastIndexOf(System.getProperty("file.separator"))+1)).split("\\."))[0]);
						}
						fis = new FileInputStream(templatepath);
						 xdoc = new XWPFDocument(fis);
						 ClassLoader classloader =
									org.apache.xmlbeans.XmlOptions.class.getClassLoader();
									URL res = classloader.getResource(
									         "org/apache/xmlbeans/XmlOptions.class");
									String path = res.getPath();
									System.out.println("Core POI came from " + path);
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
								if (p.getText().equals(rp.property().getProperty("processpara").toString())) {
									componentSectionParagraph = p;
								} else if (p.getText().equals(rp.property().getProperty("globalpara").toString())) {
									globalSectionParagraph = p;
								} else if (p.getText().equals(rp.property().getProperty("sharedconnpara").toString())) {
									sharedSectionParagraph = p;
								} else if (p.getText().equals(rp.property().getProperty("referencepara").toString())) {
									referSectionParagraph = p;
								} else if (p.getText().equals(rp.property().getProperty("intropara").toString())) {
									introSectionParagraph = p;
								}else if (p.getText().equals(rp.property().getProperty("adpaterpara").toString())) {
									adpterSectionParagraph=p;
								}else if (p.getText().equals(rp.property().getProperty("servicepara").toString())) {
									serviceSectionParagraph=p;
								}
							}
						}
						if (componentSectionParagraph != null && processlist.toArray().length != 0) {
							FileUtil.filelist = new ArrayList<String>();
							processlist = new ArrayList<String>();
							processlist = FileUtil.listFilesAndFilesSubDirectories(infolder + projectname ,
									".*.process");
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
								XmlCursor cursor9 = new_par.getCTP().newCursor();
								XWPFParagraph new_par9 = xdoc.insertNewParagraph(cursor9);
								new_par9.setStyle("Heading1");
								XWPFRun titleRun9 = new_par9.createRun();
								titleRun9.setBold(true);
								compSecPara_Format(titleRun9);
								String filepath = processlist.toArray()[x].toString().replace(System.getProperty("file.separator"), "/");
								String[] splittedprocesspath = filepath.split("/"+prjname.split(".ear")[0]+"/");	
								String processpath = null;
								for(int ek=0 ; ek < splittedprocesspath[splittedprocesspath.length - 1].split("/").length;ek++) {
									if(ek==1) {
									processpath= splittedprocesspath[splittedprocesspath.length - 1].split("/")[ek];
									}else if ( ek>1){
										processpath= processpath+"/"+ splittedprocesspath[splittedprocesspath.length - 1].split("/")[ek];
									}
								}
								String[] splittedFileName = filepath.split("/");
								String processname = splittedFileName[splittedFileName.length - 1];
								titleRun9.setText(processpara_num+"." + (x + 1) + "." + processname);
								NodeList mChildList = xmldocument.getChildNodes().item(0).getChildNodes();
								XmlCursor cursor10 = new_par.getCTP().newCursor();
								XWPFParagraph new_par10 = xdoc.insertNewParagraph(cursor10);
								XWPFRun titleRun10 = new_par10.createRun();
								titleRun10.addBreak();
								titleRun10.setFontSize(14);
								titleRun10.setText("Process  file path: " + processpath);
								transitionFlow(xdoc, xmldocument);
								int count = 1;
								for (int i = 0; i < mChildList.getLength(); i++) {
									Node node = mChildList.item(i);
									strconfig = null;
									if((node.getNodeName().equals(starter))) {
										starterobj strter = new starterobj();
											startercount++;
											String[] ActivityNameAr = node.getChildNodes().item(3).getTextContent()
													.toString().split("\\.");
											String ActivityName = ActivityNameAr[ActivityNameAr.length - 1];
											strter.setPath(processpath);
											strter.setType(ActivityName);
											XPath xPath =  XPathFactory.newInstance().newXPath();
											for(int ib=0;ib<straterconf.split(",").length;ib++) {
									         String expression = ".//"+straterconf.split(",")[ib];	 
									         NodeList nodeList =  (NodeList) xPath.compile(expression).evaluate( 
									        		 node, XPathConstants.NODESET);
									         if (nodeList.getLength() != 0) {
									             if(nodeList.item(0).getTextContent()!="" && nodeList.item(0).getTextContent()!=null) {
									            	 if(strconfig==null) {
															strconfig =  straterconf.split(",")[ib] +"=@@="+nodeList.item(0).getTextContent() ;
															}else {
																strconfig = strconfig+"@$@"+ straterconf.split(",")[ib] +"=@@="+nodeList.item(0).getTextContent() ;
															}
									             }
									         }
											}
											strter.setConfig(strconfig);
											starterlist.add(strter);
									}
									if ((node.getNodeName().equals(starter) || (node.getNodeName().equals("pd:activity")) || (node.getNodeName().equals("pd:group")))) {
										XmlCursor cursor5 = new_par.getCTP().newCursor();
										new_par5 = xdoc.insertNewParagraph(cursor5);
										new_par5.setStyle("Heading2");
										XWPFRun titleRun2 = new_par5.createRun();
										titleRun2.addBreak();
										compSecPara_Format(titleRun2);
										String[] ActivityNameAr = node.getChildNodes().item(3).getTextContent()
												.toString().split("\\.");
										String ActivityName = ActivityNameAr[ActivityNameAr.length - 1];
										titleRun2.setText(processpara_num+"." + (x + 1) + "." + count + ". "
												+ node.getAttributes().getNamedItem("name").getNodeValue() + " ( "
												+ ActivityName + " )");
										String binding = "" ;
										if(ActivityName.equals("RestAdapter")) {
											NodeList nChildListaaa = node.getChildNodes();
											for (int j = 0; j < nChildListaaa.getLength(); j++) {
												Node node1 = nChildListaaa.item(j);
												if ((node1.getNodeName()).equals(config)) {
													NodeList nChildListaab = node1.getChildNodes();
													for (int k = 0; k < nChildListaab.getLength(); k++) {
														Node node2 = nChildListaab.item(k);
														if ((node2.getNodeName()).equals("RestService")) {
															NodeList nChildListaac = node2.getChildNodes();
															for (int l = 0; l < nChildListaac.getLength(); l++) {
																Node node3 = nChildListaac.item(l);
																if ((node3.getNodeName()).equals("InnerService")) {
																	NodeList nChildListaad = node3.getChildNodes();
																	for (int m = 0; m < nChildListaad.getLength(); m++) {
																		Node node4 = nChildListaad.item(m);
																		if ((node4.getNodeName()).equals("ns0:application")) {
																			NodeList nChildListaae = node4.getChildNodes();
																			for (int n = 0; n < nChildListaae.getLength(); n++) {
																				Node node5 = nChildListaae.item(n);
																				if((!node5.getNodeName().equals(TEXT)&&node5.hasAttributes())) {
																					starterobj strter = new starterobj();
																				strter.setType(ActivityName+" ("+processpath+")");
																				strter.setPath("Service name= "+node5.getAttributes().item(1).getNodeValue()+", Basepath= "+node5.getAttributes().item(0).getNodeValue());
																				binding = "";
																				int pp=0;
																				if ((node5.getNodeName()).equals("ns0:resources")) {
																					NodeList nChildListaaf = node5.getChildNodes();
																					for (int o = 0; o < nChildListaaf.getLength(); o++) {
																						Node node6 = nChildListaaf.item(o);
																						if((!node6.getNodeName().equals(TEXT)&&(node6.hasAttributes()&& node6.getNodeName().equals("ns0:resource")))) {
																							if(binding == "") {
																							binding =binding+"/"+node6.getAttributes().item(1).getNodeValue()+"####";
																							}else {
																								binding =binding+"@@@@/"+node6.getAttributes().item(1).getNodeValue()+"####";
																							}
																						}
																						if ((node6.getNodeName()).equals("ns0:resource")) {
																							NodeList nChildListaag = node6.getChildNodes();
																							for (int p = 0; p < nChildListaag.getLength(); p++) {
																								Node node7 = nChildListaag.item(p);
																								if((!node7.getNodeName().equals(TEXT)&&(node7.hasAttributes()&& node7.getNodeName().equals("ns0:method")))) {
																									if(pp==0 || binding.endsWith("####")) {
																									binding = binding+node7.getAttributes().item(0).getNodeValue()+"&&"+node7.getAttributes().item(1).getNodeValue();
																									pp++;
																									}else {
																										binding =binding+"@===@"+node7.getAttributes().item(0).getNodeValue()+"&&"+node7.getAttributes().item(1).getNodeValue();
																										pp++;
																										}
																								}
																								if ((node7.getNodeName()).equals("ns0:method")) {
																									NodeList nChildListaah = node7.getChildNodes();
																									for (int q = 0; q < nChildListaah.getLength(); q++) {
																										Node node8 = nChildListaah.item(q);
																										if((!node8.getNodeName().equals(TEXT)&&(node8.hasAttributes() && node8.getNodeName().equals("Binding")))) {
																											binding =binding+"&&"+node8.getAttributes().item(0).getNodeValue();
																									}
																								}
																							}
																						}
																					}
																				}
																			}
																				strter.setBinding(binding);
																				starterlist.add(strter);	
																		}
																			}
																	}
																}
																}
														}
														}
												}
											}
										}
										}
										NodeList nChildLista = node.getChildNodes();
										for (int j = 0; j < nChildLista.getLength(); j++) {
											Node node1 = nChildLista.item(j);
											if ((node1.getNodeName()).equals(config)) {
												XmlCursor cursor3 = new_par.getCTP().newCursor();
												XWPFParagraph new_par3 = xdoc.insertNewParagraph(cursor3);
												XWPFRun titleRun3 = new_par3.createRun();
												titleRun3.setText(processpara_num+"." + (x + 1) + "." + count + ".1. Configurations");
												compSecPara(titleRun3);
												XmlCursor cursor6 = new_par.getCTP().newCursor();
												XWPFTable tableOne = xdoc.insertNewTbl(cursor6);
												tableOne.setWidth("100%");
												XWPFTableRow tableOneRowOne2 = tableOne.getRow(0);
												compSecPara_rowDetails(tableOneRowOne2);
												if (node1.hasChildNodes()) {
													NodeList nChildListb = node1.getChildNodes();
													for (int k = 0; k < nChildListb.getLength(); k++) {
														Node node2 = nChildListb.item(k);
														if (!(node2.getNodeName()).equals(TEXT)
																&& (!(node2.getNodeName()).equals(Headers))
																&& (!(ActivityName
																		.equals(new String("MapperActivity"))))) {
															XWPFTableRow row2 = tableOne.createRow();
															row2.getCell(0).setText(node2.getNodeName());
															row2.getCell(1).setText(node2.getTextContent());
														} else if (!(node2.getNodeName().equals(TEXT)
																&& !(node2.getNodeName()).equals(Headers))
																&& (ActivityName
																		.equals(new String("MapperActivity")))) {
															NodeList nChildListc = node2.getChildNodes();
															for (int l = 0; l < nChildListc.getLength(); l++) {
																Node node3 = nChildListc.item(l);
																if (!(node3.getNodeName().equals(TEXT))) {
																	XWPFTableRow row2 = tableOne.createRow();
																	row2.getCell(0).setText(node3.getAttributes()
																			.item(0).getNodeValue());
																	NodeList nChildListd = node3.getChildNodes();
																	for (int m = 0; m < nChildListd.getLength(); m++) {
																		Node node4 = nChildListd.item(m);
																		if (!(node4.getNodeName()).equals(TEXT)) {
																			row2.getCell(1)
																					.setText(node4.getNodeName());
																		}
																	}
																}
															}
														}
													}
												}
											}
											if ((node1.getNodeName()).equals(inputBindings)) {
												XmlCursor cursor3 = new_par.getCTP().newCursor();
												XWPFParagraph new_par3 = xdoc.insertNewParagraph(cursor3);
												XWPFRun titleRun3 = new_par3.createRun();
												titleRun3.setText(processpara_num+"." + (x + 1) + "." + count + ".2. Mappings");
												compSecPara(titleRun3);
												XmlCursor cursor4 = new_par.getCTP().newCursor();
												XWPFTable tableOne2 = xdoc.insertNewTbl(cursor4);
												XWPFTableRow tableOneRowOne2 = tableOne2.getRow(0);
												compSecPara_rowDetails(tableOneRowOne2);
												tableOne2.setWidth("100%");
												inputBindings(node1, tableOne2);
											}
										if((node.getNodeName().equals("pd:group") && (node1.getNodeName()).equals("pd:activity"))) {
											count++;
											activitiescount++;
											XmlCursor cursor51 = new_par.getCTP().newCursor();
											new_par5 = xdoc.insertNewParagraph(cursor51);
											new_par5.setStyle("Heading2");
											XWPFRun titleRun21 = new_par5.createRun();
											titleRun21.addBreak();
											compSecPara_Format(titleRun21);
											String[] ActivityNameAr1 = node1.getChildNodes().item(3).getTextContent()
													.toString().split("\\.");
											String ActivityName1 = ActivityNameAr1[ActivityNameAr1.length - 1];
											titleRun21.setText(processpara_num+"." + (x + 1) + "." + count + ". "
													+ node1.getAttributes().getNamedItem("name").getNodeValue() + " ( "
													+ ActivityName1 + " )");
											NodeList nChildLista1 = node1.getChildNodes();
											for (int s = 0; s < nChildLista1.getLength(); s++) {
												Node node2 = nChildLista1.item(s);
												if ((node2.getNodeName()).equals(config)) {
													XmlCursor cursor32 = new_par.getCTP().newCursor();
													XWPFParagraph new_par32 = xdoc.insertNewParagraph(cursor32);
													XWPFRun titleRun32 = new_par32.createRun();
													titleRun32.setText(processpara_num+"." + (x + 1) + "." + count + ".1. Configurations");
													compSecPara(titleRun32);
													XmlCursor cursor62 = new_par.getCTP().newCursor();
													XWPFTable tableOne2 = xdoc.insertNewTbl(cursor62);
													tableOne2.setWidth("100%");
													XWPFTableRow tableOneRowOne2 = tableOne2.getRow(0);
													compSecPara_rowDetails(tableOneRowOne2);
													if (node2.hasChildNodes()) {
														NodeList nChildListb = node2.getChildNodes();
														for (int q = 0; q < nChildListb.getLength(); q++) {
															Node node22 = nChildListb.item(q);
															if (!(node22.getNodeName()).equals(TEXT)
																	&& (!(node22.getNodeName()).equals(Headers))
																	&& (!(ActivityName1
																			.equals(new String("MapperActivity"))))) {
																XWPFTableRow row22 = tableOne2.createRow();
																row22.getCell(0).setText(node22.getNodeName());
																row22.getCell(1).setText(node22.getTextContent());
															} else if (!(node22.getNodeName().equals(TEXT)
																	&& !(node22.getNodeName()).equals(Headers))
																	&& (ActivityName1
																			.equals(new String("MapperActivity")))) {
																NodeList nChildListw = node2.getChildNodes();
																for (int w = 0; w < nChildListw.getLength(); w++) {
																	Node node32 = nChildListw.item(w);
																	if(node32.hasAttributes()) {
																	if (!(node32.getNodeName().equals(TEXT) && node32.getAttributes().item(0).getNodeValue().isEmpty())) {
																		XWPFTableRow row22 = tableOne2.createRow();
																		row22.getCell(0).setText(node32.getAttributes()
																				.item(0).getNodeValue());
																		NodeList nChildListu = node32.getChildNodes();
																		for (int u = 0; u < nChildListu.getLength(); u++) {
																			Node node42 = nChildListu.item(u);
																			if (!(node42.getNodeName()).equals(TEXT)) {
																				row22.getCell(1)
																						.setText(node42.getNodeName());
																			}
																		}
																	}
																}
																}
															}
														}
													}
												}
												if ((node2.getNodeName()).equals(inputBindings)) {
													XmlCursor cursor31 = new_par.getCTP().newCursor();
													XWPFParagraph new_par31 = xdoc.insertNewParagraph(cursor31);
													XWPFRun titleRun31 = new_par31.createRun();
													titleRun31.setText(processpara_num+"." + (x + 1) + "." + count + ".2. Mappings");
													compSecPara(titleRun31);
													XmlCursor cursor41 = new_par.getCTP().newCursor();
													XWPFTable tableOne21 = xdoc.insertNewTbl(cursor41);
													XWPFTableRow tableOneRowOne21 = tableOne21.getRow(0);
													compSecPara_rowDetails(tableOneRowOne21);
													tableOne21.setWidth("100%");
													inputBindings(node2, tableOne21);
												}
											}
										}
										}
										count++;
										activitiescount++;
									}
								}
								proccesscount++;
							}
						}
						if (sharedSectionParagraph != null && sharedlist.toArray().length != 0) {
							FileUtil.filelist = new ArrayList<String>();
							 ArrayList<String> filelistq = FileUtil
									.listFilesAndFilesSubDirectories(infolder + projectname,sharedconn_regex);
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
								new_par9.setStyle("Heading2");
								XWPFRun titleRun9 = new_par9.createRun();
								compSecPara_Format(titleRun9);
								String filepath = filelistq.toArray()[x].toString().replace(System.getProperty("file.separator"), "/");
								String[] splittedFileName = filepath.split("/");
								String processname = splittedFileName[splittedFileName.length - 1];
								String[] splittedshareFileName = filepath.split(prjname.split(".ear")[0]);
								String processname1 = splittedshareFileName[1];
								titleRun9.addBreak();
								titleRun9.setText(sharedconnpara_num+"." + (x + 1) + "." + processname);
								XmlCursor cursor10 = new_par.getCTP().newCursor();
								XWPFParagraph new_par10 = xdoc.insertNewParagraph(cursor10);
								XWPFRun titleRun10 = new_par10.createRun();
								titleRun10.addBreak();
								titleRun10.setFontSize(14);
								titleRun10.setText("Connection Path: " + processname1);
								titleRun10.addBreak();
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
								}
								sharedconncount++;
							}
						}
						if (adpterSectionParagraph != null && adpterlist.toArray().length != 0) {
							FileUtil.filelist = new ArrayList<String>();
							 ArrayList<String> filelistq = FileUtil
									.listFilesAndFilesSubDirectories(infolder + projectname+"/adapter/",".*.xml");
							 for (int x = 0; x < filelistq.size(); x++) {
								XmlCursor cursor;
								if (x == 0) {
									cursor = adpterSectionParagraph.getCTP().newCursor();
									cursor.toNextSibling();
									new_par = xdoc.insertNewParagraph(cursor);
								} else {
									cursor = new_par.getCTP().newCursor();
									new_par = xdoc.insertNewParagraph(cursor);
								}
								XmlCursor cursor9 = new_par.getCTP().newCursor();
								XWPFParagraph new_par9 = xdoc.insertNewParagraph(cursor9);
								new_par9.setStyle("Heading2");
								XWPFRun titleRun9 = new_par9.createRun();
								compSecPara_Format(titleRun9);
								String filepath = filelistq.toArray()[x].toString().replace(System.getProperty("file.separator"), "/");
								String[] splittedFileName = filepath.split("/");
								String processname = splittedFileName[splittedFileName.length - 2];
								String[] splittedshareFileName = filepath.split(prjname.split(".ear")[0]);
								String processname1 = splittedshareFileName[1];
								titleRun9.addBreak();
								titleRun9.setText(adapterconnpara_num+"." + (x + 1) + "." + processname);
								XmlCursor cursor10 = new_par.getCTP().newCursor();
								XWPFParagraph new_par10 = xdoc.insertNewParagraph(cursor10);
								XWPFRun titleRun10 = new_par10.createRun();
								titleRun10.addBreak();
								titleRun10.setFontSize(14);
								titleRun10.setText("Adapter Configuration path: " + processname1);
								titleRun10.addBreak();
								org.w3c.dom.Document xmldocument;
								DocumentBuilder builder = readDoc();
								xmldocument = builder.parse(new File((String) filelistq.toArray()[x]));
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
															nChildListc.item(1).getTextContent().replace("/", "."));
													row2.getCell(1).setText(nChildListc.item(3).getTextContent());
												}
											}
										}
									}
								}
								adacount++;
							}
						}
						if (globalSectionParagraph != null) {
							XmlCursor cursor;
							cursor = globalSectionParagraph.getCTP().newCursor();
							cursor.toNextSibling();
							new_par = xdoc.insertNewParagraph(cursor);
							org.w3c.dom.Document xmldocument;
							DocumentBuilder builder = readDoc();
							xmldocument = builder.parse(new File(infolder + projectname + "/TIBCO.xml"));
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
														nChildListc.item(1).getTextContent().replace("/", "."));
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
						int serviceagnt = 0;
						if(serviceSectionParagraph != null) {
							int strtcount = 1;
							XmlCursor cursor;
								cursor = serviceSectionParagraph.getCTP().newCursor();
								cursor.toNextSibling();
								new_par = xdoc.insertNewParagraph(cursor);
								XmlCursor cursor9 = new_par.getCTP().newCursor();
								XWPFParagraph new_par9 = xdoc.insertNewParagraph(cursor9);
								XWPFRun titleRun9 = new_par9.createRun();
								titleRun9.setBold(true);
								titleRun9.setText("Service Agent and other HTTP services");
								XmlCursor cursor12 = new_par9.getCTP().newCursor();
								cursor12.toNextSibling();
								XWPFTable tableTwo = xdoc.insertNewTbl(cursor12);
								tableTwo.setWidth("100%");
								XWPFTableRow tableOneRowOneTwo = tableTwo.getRow(0);
								tableOneRowOneTwo.getCell(0).setText("Service");
								tableOneRowOneTwo.addNewTableCell().setText("Operation");
								tableOneRowOneTwo.addNewTableCell().setText("implementation");
								introSecPara_Format(tableOneRowOneTwo);
								tableOneRowOneTwo.getCell(2).setColor("c6e5f5");
							FileUtil.filelist = new ArrayList<String>();
							processlist = new ArrayList<String>();
							processlist = FileUtil.listFilesAndFilesSubDirectories(infolder + projectname ,
									".*.serviceagent");
							for (int x = 0; x < processlist.size(); x++) {
								org.w3c.dom.Document xmldocument;
								DocumentBuilder builder = readDoc();
								xmldocument = builder.parse(new File((String) processlist.toArray()[x]));
								((org.w3c.dom.Document) xmldocument).getDocumentElement().normalize();
								String filepath = processlist.toArray()[x].toString().replace(System.getProperty("file.separator"), "/");
								String[] splittedprocesspath = filepath.split("/"+prjname.split(".ear")[0]+"/");	
								String processpath = null;
								for(int ek=0 ; ek < splittedprocesspath[splittedprocesspath.length - 1].split("/").length;ek++) {
									if(ek==1) {
									processpath= splittedprocesspath[splittedprocesspath.length - 1].split("/")[ek];
									}else if ( ek>1){
										processpath= processpath+"/"+ splittedprocesspath[splittedprocesspath.length - 1].split("/")[ek];
									}
								}
								String[] splittedFileName = filepath.split("/");
								String processname = splittedFileName[splittedFileName.length - 1];
								NodeList mChildList = xmldocument.getChildNodes().item(0).getChildNodes();
								for (int i = 0; i < mChildList.getLength(); i++) {
									Node node = mChildList.item(i);
									if ((node.getNodeName()).equals(config)) {
										NodeList nChildListaaa = node.getChildNodes();
										for (int a = 0; a < nChildListaaa.getLength(); a++) {
											Node node1 = nChildListaaa.item(a);
											if ((node1.getNodeName()).equals("interfaceImpl")) {
												NodeList nChildListaab = node1.getChildNodes();
												for(int b = 0; b < nChildListaab.getLength(); b++) {
													Node node2 = nChildListaab.item(b);
													if ((node2.getNodeName()).equals("tab")) {
														NodeList nChildListaac = node2.getChildNodes();
														for (int c = 0; c < nChildListaac.getLength(); c++) {
															Node node3 = nChildListaac.item(c);
															if ((node3.getNodeName()).equals(config)) {
																NodeList nChildListaad = node3.getChildNodes();
																for (int d = 0; d < nChildListaad.getLength(); d++) {
																	Node node4 = nChildListaad.item(d);
																	if ((node4.getNodeName()).equals("detail")) {
																		NodeList nChildListaae = node4.getChildNodes();
																		for(int e = 0; e < nChildListaae.getLength(); e++) {
																		Node node5 = nChildListaae.item(e);
																		if((node5.getNodeName()).equals("tab")) {
																		NodeList nChildListaaf = node5.getChildNodes();
																		for (int f = 0; f < nChildListaaf.getLength(); f++) {
																			Node node6 = nChildListaaf.item(f);
																			if ((node6.getNodeName()).equals(config)) {
																				NodeList nChildListaag = node6.getChildNodes();
																				for (int g = 0; g < nChildListaag.getLength(); g++) {
																					Node node7 = nChildListaag.item(g);
																					if ((node7.getNodeName()).equals("operations")) {
																						NodeList nChildListaah = node7.getChildNodes();
																						for (int h = 0; h < nChildListaah.getLength(); h++) {
																							Node node8 = nChildListaah.item(h);
																							if(node8.getNodeName().equals("row")&&node8.hasAttributes()) {
																								XWPFTableRow row7 = tableTwo.createRow();
																								row7.getCell(0).setText(processname.split(".serviceagent")[0]+" (" +processpath+" )");
																								row7.getCell(1).setText(node8.getAttributes().item(1).getNodeValue());
																								row7.getCell(2).setText(node8.getAttributes().item(0).getNodeValue());
																							}
																						}
																					}
																				}
																			}
																		}
																		}
																	}
																	}
																}
															}
														}
													}
												}
											}
											}
									}
								}
								strtcount++;
								serviceagnt++;
							}
							for (starterobj starter : starterlist) {
								if(starter.getType().contains("RestAdapter") || starter.getType().equals("SOAPEventSourceUI") || starter.getType().equals("httpEventSource")) {							
									if(starter.getType().contains("RestAdapter")) {
								for(int k= 0; k<starter.getBinding().split("@@@@").length;k++) {
									String operations=(starter.getBinding().split("@@@@")[k]).split("####")[1];
							 if(!((operations).contains("@===@"))) {
									 XWPFTableRow row8 = tableTwo.createRow();
									 row8.getCell(0).setText(starter.getType());
									 row8.getCell(1).setText(((operations).split("&&")[0])+"("+ ((operations).split("&&")[1])+" )");
									 row8.getCell(2).setText(((operations).split("&&")[2])); 
								 }else {
									 String[] ops= ((operations).split("@===@"));
									for(int l=0;l<ops.length;l++) {
										 XWPFTableRow row9 = tableTwo.createRow();
										 row9.getCell(0).setText(starter.getType());
										 row9.getCell(1).setText(ops[l].split("&&")[0]+"( "+ops[l].split("&&")[1]+" )");
										 row9.getCell(2).setText(ops[l].split("&&")[2]);
									}
								 }
								}
								}
								if((starter.getType().equals("SOAPEventSourceUI") || starter.getType().equals("httpEventSource"))&&starter.getConfig() != null) {
							    	String[] values = starter.getConfig().toString().split("@\\$@");
							    	   XWPFTableRow row7 = tableTwo.createRow();
								        row7.getCell(0).setText(starter.getType());
								        row7.getCell(1).setText(starter.getPath());
								        String config = "";
							     for(int w=0 ;w < values.length;w++) {
							    	if(config=="") {
							    		config = (values[w]).split("=@@=")[0] + "="+ (values[w]).split("=@@=")[1];
							    	}
							    	else {
							    		config = config+" , "+(values[w]).split("=@@=")[0] + "="+ (values[w]).split("=@@=")[1];
							    	}
							     }
							     row7.getCell(2).setText(config);
						     }
								strtcount++;
								}
							}
							XmlCursor cursor11 = new_par.getCTP().newCursor();
							cursor11.toNextSibling();
							XWPFParagraph new_par2 = xdoc.insertNewParagraph(cursor11);
							XWPFRun titleRun10 = new_par2.createRun();
							titleRun10.setBold(true);
							titleRun10.setText("Other starters details");
							XmlCursor cursor13 = new_par2.getCTP().newCursor();
							cursor13.toNextSibling();
							XWPFTable tableThree = xdoc.insertNewTbl(cursor13);
							tableThree.setWidth("100%");
							XWPFTableRow tableOneRowOneThree = tableThree.getRow(0);
							tableOneRowOneThree.getCell(0).setText("Starter");
							tableOneRowOneThree.addNewTableCell().setText("Process Name");
							tableOneRowOneThree.addNewTableCell().setText("Config");
							introSecPara_Format(tableOneRowOneThree);
							tableOneRowOneThree.getCell(2).setColor("c6e5f5");
							for (starterobj starter : starterlist) {
								if((!starter.getType().contains("RestAdapter")) && (!starter.getType().equals("SOAPEventSourceUI")) && (!starter.getType().equals("httpEventSource"))) {
						    if(starter.getConfig() != null) {
							    	String[] values = starter.getConfig().toString().split("@\\$@");
							    	   XWPFTableRow row7 = tableThree.createRow();
								        row7.getCell(0).setText(starter.getType());
								        row7.getCell(1).setText(starter.getPath());
								        String config = "";
							     for(int w=0 ;w < values.length;w++) {
							    	if(config=="") {
							    		config = (values[w]).split("=@@=")[0] + "="+ (values[w]).split("=@@=")[1];
							    	}
							    	else {
							    		config = config+" , "+(values[w]).split("=@@=")[0] + "="+ (values[w]).split("=@@=")[1];
							    	}
							     }
							     row7.getCell(2).setText(config);
						    }}
						     strtcount++;
							}
						}
						if (introSectionParagraph != null) {
							XmlCursor cursor;
							cursor = introSectionParagraph.getCTP().newCursor();
							cursor.toNextSibling();
							new_par = xdoc.insertNewParagraph(cursor);
							XWPFRun titleRun9 = new_par.createRun();
							titleRun9.setText(rp.property().getProperty("introduction").toString());
							titleRun9.addBreak();
							titleRun9.setText("Project File Name: " + prjname);
							titleRun9.addBreak();
							titleRun9.setText("File Size: " + infilesize);
							titleRun9.addBreak();
							DateFormat dx = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
							titleRun9.setText("Document created on: " + dx.format(dateobj));
							titleRun9.addBreak();
							XmlCursor cursor11 = new_par.getCTP().newCursor();
							cursor11.toNextSibling();
							XWPFParagraph new_par2 = xdoc.insertNewParagraph(cursor11);
							XWPFRun titleRun10 = new_par2.createRun();
							titleRun10.setBold(true);
							titleRun10.setText("Archive statistic:");
							titleRun10.addBreak();
							XmlCursor cursor3 = new_par2.getCTP().newCursor();
							cursor3.toNextSibling();
							XWPFTable tableOne = xdoc.insertNewTbl(cursor3);
							tableOne.setWidth("100%");
							XWPFTableRow tableOneRowOne2 = tableOne.getRow(0);
							tableOneRowOne2.getCell(0).setText("Type");
							tableOneRowOne2.addNewTableCell().setText("Count");
							introSecPara_Format(tableOneRowOne2);
							XWPFTableRow row2 = tableOne.createRow();
							row2.getCell(0).setText("Total Processes");
							row2.getCell(1).setText("" + proccesscount);
							XWPFTableRow row21 = tableOne.createRow();
							row21.getCell(0).setText("Starter Processes");
							row21.getCell(1).setText("" + startercount);
							XWPFTableRow row22 = tableOne.createRow();
							row22.getCell(0).setText("Service Agents");
							row22.getCell(1).setText("" + serviceagnt);
							XWPFTableRow row4 = tableOne.createRow();
							row4.getCell(0).setText("Activities");
							row4.getCell(1).setText("" + activitiescount);
							XWPFTableRow row3 = tableOne.createRow();
							row3.getCell(0).setText("Shared Connections");
							row3.getCell(1).setText("" + sharedconncount);
							XWPFTableRow row6 = tableOne.createRow();
							row6.getCell(0).setText("Adapter Configurations");
							row6.getCell(1).setText("" + adacount);
							XWPFTableRow row5 = tableOne.createRow();
							row5.getCell(0).setText("GVs");
							row5.getCell(1).setText("" + gvcount);
						}
						startercount=0;
						File directory = new File(outfolder);
					    if (! directory.exists()){
					        directory.mkdir();
					    }
						FileOutputStream out = new FileOutputStream(
								outfolder + outfilename);
						xdoc.write(out);
						out.close();
						xdoc.close();
						File projectpath = new File(destDir);
						FileUtil.deleteDirectory(projectpath);
						FileUtil.rename(infolder + projectname + ".zip", infolder + projectname + ".ear");
						System.out.println(
								"Desgin Document is created at " + System.getProperty("user.dir").replace(System.getProperty("file.separator"), "/")
										+ "/" + outfolder + outfilename );
						outstring = "success";
					}
			} else {
				throw new FileNotFoundException("Ear files not found in the input folder");
			}
		} catch (Exception e) {
			e.printStackTrace();
			FileUtil.rename(rp.property().getProperty("earinfolder").toString() + projectname + ".zip",
					rp.property().getProperty("earinfolder").toString() + projectname + ".ear");
			File projectpath = new File(rp.property().getProperty("earinfolder").toString()+projectname);
			FileUtil.deleteDirectory(projectpath);
		}
		return outstring;
	}
	private static void transitionFlow(XWPFDocument xdoc, org.w3c.dom.Document xmldocument) {NodeList nList = xmldocument.getElementsByTagName(transition);
	String Trns5 = new String();
	String Trns6 = new String();
	NodeList stratList = xmldocument.getElementsByTagName(starter);
	NodeList GroupList = xmldocument.getElementsByTagName("pd:group");
	NodeList stratNameList = xmldocument.getElementsByTagName("pd:startName");
	Node stratNamenode = stratNameList.item(0);
	ArrayList<String> startArray = new ArrayList<String>();
	ArrayList<String> groupArray = new ArrayList<String>();
	String Finalmap = new String();
	String mapkey = new String();
	String mapvalue = new String();
	String groupmapkey = new String();
	String groupmapvalue = new String();
	 HashMap<String,String> hm =new HashMap<String,String>(); 
	 HashMap<String,String> hm2 =new HashMap<String,String>();  
	 XmlCursor cursor11 = new_par.getCTP().newCursor();
		XWPFParagraph new_par11 = xdoc.insertNewParagraph(cursor11);
		XWPFRun titleRun11 = new_par11.createRun();
	  int count =new Integer(0);
	  int dupcount =new Integer(0);
	  String refinal = null;
		try {
			for (int tr = 0; tr < stratList.getLength(); tr++) {
			Node node8 = stratList.item(tr);
			if(node8.hasAttributes()) {
			if(!(node8.getAttributes().item(0).getNodeValue().equals("Start") && !(node8.getAttributes().item(0).getNodeValue().equals("start")))) {
			startArray.add(node8.getAttributes().item(0).getNodeValue());
			}
			}
		}
			for (int tr = 0; tr < GroupList.getLength(); tr++) {
				Node node8 = GroupList.item(tr);
				if(node8.hasAttributes()) {
					groupArray.add(node8.getAttributes().item(0).getNodeValue());
				}
			}
		  for (int tr = 0; tr < nList.getLength(); tr++) { 
			  Node node8 = nList.item(tr);
			  Element eElement = (Element) node8; 
			  if (node8.getNodeType() == Node.ELEMENT_NODE) {
		  if (eElement.getElementsByTagName("pd:from").item(0).getTextContent().equals("Start")) {
		  Trns5 = "Start";
		  Trns6 =new String(eElement.getElementsByTagName("pd:to").item(0).getTextContent());
		  }
		  else{
			  if(!eElement.getElementsByTagName("pd:from").item(0).getTextContent().equals("start") && !eElement.getElementsByTagName("pd:to").item(0).getTextContent().equals("end")){
			  Trns5 =new String(eElement.getElementsByTagName("pd:from").item(0).getTextContent());
			  Trns6 =new String(eElement.getElementsByTagName("pd:to").item(0).getTextContent());
			  }
		  }
		  if ((hm.containsKey(Trns5))) {
			  Trns5 = Trns5+"_0_"+Trns6+"@";
			  Trns6 =new String(eElement.getElementsByTagName("pd:to").item(0).getTextContent());
		  }
		  if(!Trns5.equals(null)&& !Trns5.equals("")&&!Trns6.equals(null)&&!Trns6.equals("")) {
		  hm.put(Trns5, Trns6); 
		  }
		  } }
		  Set<String> start = hm.keySet()
                     .stream()
                     .filter(s -> s.startsWith("Start") && s.endsWith("@")&& s.contains("_0_"))
                     .collect(Collectors.toSet());
			 Set<String> dupkey = hm.keySet()
                     .stream()
                     .filter(s -> (!(s.startsWith("Start"))) && (s.endsWith("@") && s.contains("_0_")))
                     .collect(Collectors.toSet());
		  for (int tr = 0; tr < 1000; tr++) {
			  if(((mapkey != null &&(mapkey.startsWith("Start"))) || (tr==0 && hm.containsKey("Start"))) || (startArray.size() != 0 && (tr == 0 && hm.containsKey(startArray.toArray()[tr])))) {
				  if (mapkey.startsWith("Start")){
				  		mapkey = hm.get(mapkey);
				  		 Finalmap = "Start"+transsepartor+ mapkey;
				  	}
				else if(tr==0 && (startArray.size() != 0 && (tr == 0 && hm.containsKey(startArray.toArray()[tr])))) {
				  	mapkey = hm.get(startArray.toArray()[tr]);
				  	 Finalmap = startArray.toArray()[tr]+transsepartor+ mapkey;
				  	}
				else if (tr==0 && !(startArray.size() != 0 && (tr == 0 && hm.containsKey(startArray.toArray()[tr])))){
				  		mapkey = hm.get("Start");
				  		 Finalmap = "Start"+transsepartor+ mapkey;
				  	}
				else if(tr==0 && hm.containsKey(stratNamenode.getTextContent()) ) {
					mapkey = hm.get(stratNamenode.getTextContent());
			  		 Finalmap = stratNamenode.getTextContent()+transsepartor+ mapkey;
				}
			  }
			  else {
				  mapvalue = hm.get(mapkey);
				  Finalmap = Finalmap +transsepartor+ mapvalue;
				  refinal = Finalmap;
				  mapkey = mapvalue;
				  if(mapvalue != null) {
				  if ((mapvalue.equals(new String("End")) && Finalmap != null && Finalmap != "")|| !hm.containsKey(mapvalue)) {
					  titleRun11.addBreak();
						titleRun11.setFontSize(12);
						titleRun11.setColor("031E70");
						titleRun11.setText(transheader+" "+ Finalmap);
						titleRun11.addBreak();
					 if(start.toArray().length != 0 && start.toArray().length > count && mapvalue.equals(new String("End"))) {
					  mapkey=start.toArray()[count].toString();
					  count ++;
					  refinal = Finalmap;
					 } else {
					 if((dupkey.toArray().length != 0 && dupcount < dupkey.toArray().length) && mapvalue.equals(new String("End"))) {
						  mapkey=dupkey.toArray()[dupcount].toString();
						  Finalmap = refinal.split(mapkey.split("_0_")[0])[0]+ mapkey.split("_0_")[0];
						  mapkey=(dupkey.toArray()[dupcount].toString().split("_0_")[1]).split("@")[0];
						  Finalmap=Finalmap+transsepartor+mapkey;
						  dupcount ++;
						  refinal = Finalmap;
						  }
					 else{
					 if(hm.containsKey("Catch")) {
						 mapkey = hm.get("Catch");
						 Finalmap= "Catch" + transsepartor+mapkey;
						 refinal = Finalmap;
						 hm.remove("Catch");
					 }
					 }		 
				  }
				  }
				  if(groupArray.contains(mapvalue)) {
					  NodeList GroupList2 = xmldocument.getElementsByTagName("pd:group");
					  for (int tr2 = 0; tr2 < GroupList2.getLength(); tr2++) {
							Node node7 = GroupList2.item(tr2);
							if(node7.hasAttributes() && node7.getAttributes().item(0).getNodeValue().equals(mapvalue)) {
								NodeList nChildList = node7.getChildNodes();
								 hm2 =new HashMap<String,String>();  
								for(int tr3 = 0; tr3 < nChildList.getLength(); tr3++) {
									  Node node8 = nChildList.item(tr3);
									if(node8.getNodeName().equals(transition)) {
										NodeList yChildList = node8.getChildNodes();
										for(int tr4 = 0; tr4 < yChildList.getLength(); tr4++) {
											 Node node9 = yChildList.item(tr4);
											if(node9.getNodeName().equals("pd:from") && !node9.getNodeName().equals(TEXT)) {
												 Trns5 =new String(node9.getTextContent().toString());	
											}
											if(node9.getNodeName().equals("pd:to") && !node9.getNodeName().equals(TEXT)) {
												 Trns6 =new String(node9.getTextContent().toString());	
											}
										}
										 if ((hm2.containsKey(Trns5))) {
											  Trns5 = Trns5+"_0_"+Trns6+"@";
										  }
										if(!Trns5.equals(null)&& !Trns5.equals("")&&!Trns6.equals(null)&&!Trns6.equals("") ) {
											hm2.put(Trns5, Trns6); 
										}
									}
								}
							}
						}
					  Set<String> start2 = hm2.keySet()
			                     .stream()
			                     .filter(s -> s.startsWith("start") && s.endsWith("@")&& s.contains("_0_"))
			                     .collect(Collectors.toSet());
						 Set<String> dupkey2 = hm2.keySet()
			                     .stream()
			                     .filter(s -> (!(s.startsWith("start"))) && (s.endsWith("@") && s.contains("_0_")))
			                     .collect(Collectors.toSet());
						 int count2 =new Integer(0);
						  int dupcount2 =new Integer(0);
					  for (int tr5 = 0; tr5 < 500; tr5++) {
						  if(tr5==0 && hm2.containsKey("start")) {
							  Finalmap = Finalmap +groupsepartor_start+" start";
							  groupmapkey="start";
						  }else {
							  groupmapkey=hm2.get(groupmapkey);
							  groupmapvalue=hm2.get(groupmapkey);
							  if(groupmapvalue != null) {
								  Finalmap = Finalmap+transsepartor+groupmapkey;
							  if(groupmapvalue.equals("end")) {
								  if((start2.toArray().length != 0 && start2.toArray().length > count2) || (dupkey2.toArray().length != 0 &&   dupkey2.toArray().length > dupcount2)) {
									  Finalmap=Finalmap+transsepartor+"end "+grouptransepartor;  
								  }else {
								  Finalmap=Finalmap+transsepartor+"end "+groupsepartor_end; 
								  }
								  if(start2.toArray().length != 0 && start2.toArray().length > count2 && groupmapvalue.equals(new String("end"))) {
									  groupmapkey=start2.toArray()[count2].toString();
									  start2.remove(start2.toArray()[count2].toString());
									  count2 ++;
									  Finalmap = Finalmap+" start";									  
									 } else {
									 if((dupkey2.toArray().length != 0 && dupcount2 < dupkey2.toArray().length) && groupmapvalue.equals(new String("end"))) {
										 groupmapkey=dupkey2.toArray()[dupcount].toString();
										  Finalmap = refinal.split(groupmapkey.split("_0_")[0])[0]+ groupmapkey.split("_0_")[0];
										  groupmapkey=(dupkey2.toArray()[dupcount2].toString().split("_0_")[1]).split("@")[0];
										  Finalmap=Finalmap+transsepartor+groupmapkey;
										  dupkey2.remove(dupkey2.toArray()[dupcount].toString());
										  dupcount2 ++;
										  refinal = Finalmap;
										  }
									 else{
									 if(hm2.containsKey("Catch")) {
										 groupmapkey = hm.get("Catch");
										 Finalmap= "Catch" + transsepartor+groupmapkey;
										 refinal = Finalmap;
										 hm2.remove("Catch");
									 }
									 }	
							  }}
						  }
						  }
					  }
				  }
			  }
			  }
		  }
		  Finalmap = new String();
		  refinal = new String();
		  hm =new HashMap<String,String>();   
		}
	 catch (Exception e) {
		e.printStackTrace();
	}}
	private static void inputBindings(Node node1, XWPFTable tableOne2) {
		if (node1.hasChildNodes()) {
			NodeList nChildListb = node1.getChildNodes();
			for (int k = 0; k < nChildListb.getLength(); k++) {
				Node node2 = nChildListb.item(k);
				if (!(node2.getNodeName()).equals(TEXT)) {
					if (node2.hasAttributes() && !(node2.getNodeName()).equals(variable)
							&& node2.getNodeName().startsWith("xsl:")) {
						if ((node2.getAttributes().item(0).getNodeName().equals("test") || node2.getAttributes().item(0).getNodeName().equals("select")) && node2.getAttributes().getLength() == 1) {
							XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
							tableOneRowOne01.getCell(0).setText(node2.getNodeName());
							tableOneRowOne01.getCell(1).setText(node2.getAttributes().item(0).getNodeValue());
						}
						if (node2.getAttributes().getLength() == 2 && node2.getAttributes().item(1).getNodeName().equals("select")) {
								XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
								tableOneRowOne01.getCell(0).setText(node2.getAttributes().item(0).getNodeValue());
								tableOneRowOne01.getCell(1).setText(node2.getAttributes().item(1).getNodeValue());
						}
					}
					if (node2.hasChildNodes()) {
						NodeList nChildListc = node2.getChildNodes();
						for (int l = 0; l < nChildListc.getLength(); l++) {
							Node node3 = nChildListc.item(l);
							if (!(node3.getNodeName()).equals(TEXT)) {
								if (node3.hasAttributes() && !(node3.getNodeName()).equals(variable)
										&& node3.getNodeName().startsWith("xsl:")) {
									if (node3.getAttributes().item(0).getNodeName().equals("test")
											|| (node3.getAttributes().item(0).getNodeName().equals("select")
													&& !(node3.getNodeName().equals("xsl:value-of")))) {
										XWPFTableRow tableOneRowOne02 = tableOne2.createRow();
										tableOneRowOne02.getCell(0)
												.setText(node2.getNodeName() + "/" + node3.getNodeName());
										tableOneRowOne02.getCell(1)
												.setText(node3.getAttributes().item(0).getNodeValue());
									}
									if (node3.getAttributes().getLength() == 2
											&& node3.getNodeName().startsWith("xsl:")) {
										if (node3.getAttributes().item(1).getNodeName().equals("select")) {
											XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
											tableOneRowOne01.getCell(0)
													.setText(node2.getNodeName() + "/" + node3.getNodeName());
											tableOneRowOne01.getCell(1)
													.setText(node3.getAttributes().item(1).getNodeValue());
										}
									}
								}
								if (node3.hasChildNodes()) {
									NodeList nChildListd = node3.getChildNodes();
									for (int m = 0; m < nChildListd.getLength(); m++) {
										Node node4 = nChildListd.item(m);
										if (!(node4.getNodeName()).equals(TEXT)) {
													if (node4.hasAttributes()
													&& !(node4.getNodeName()).equals(variable)
													&& node4.getNodeName().startsWith("xsl:")) {
												if (node4.getAttributes().item(0).getNodeName().equals("test")
														|| (node4.getAttributes().item(0).getNodeName().equals("select")
																&& !(node4.getNodeName().equals("xsl:value-of")))) {
													XWPFTableRow tableOneRowOne03 = tableOne2.createRow();
													tableOneRowOne03.getCell(0).setText(node2.getNodeName() + "/"
															+ node3.getNodeName() + "/" + node4.getNodeName());
													tableOneRowOne03.getCell(1)
															.setText(node4.getAttributes().item(0).getNodeValue());
												}
												if (node4.getAttributes().getLength() == 2
														&& node4.getNodeName().startsWith("xsl:")) {
													if (node4.getAttributes().item(1).getNodeName().equals("select")) {
														XWPFTableRow tableOneRowOne01 = tableOne2.createRow();
														tableOneRowOne01.getCell(0).setText(node2.getNodeName() + "/"
																+ node3.getNodeName() + "/" + node4.getNodeName());
														tableOneRowOne01.getCell(1)
																.setText(node4.getAttributes().item(1).getNodeValue());
													}
												}
											}
											if (node4.hasChildNodes()) {
												NodeList nChildListe = node4.getChildNodes();
												for (int n = 0; n < nChildListe.getLength(); n++) {
													Node node5 = nChildListe.item(n);
													if ((!(node5.getNodeName()).equals(TEXT))
															&& node5.getNodeName() != null) {
														node5 = nChildListe.item(n);
														if (node5.hasAttributes()
																&& !(node5.getNodeName()).equals(variable)
																&& node5.getNodeName().startsWith("xsl:")) {
															if (node5.getAttributes().item(0).getNodeName()
																	.equals("test")
																	|| (node5.getAttributes().item(0).getNodeName()
																			.equals("select")
																			&& !(node5.getNodeName()
																					.equals("xsl:value-of")))) {
																XWPFTableRow tableOneRowOne03 = tableOne2.createRow();
																tableOneRowOne03.getCell(0)
																		.setText(node2.getNodeName() + "/"
																				+ node3.getNodeName() + "/"
																				+ node4.getNodeName() + "/"
																				+ node5.getNodeName());
																tableOneRowOne03.getCell(1).setText(
																		node5.getAttributes().item(0).getNodeValue());
															}
															if (node5.getAttributes().getLength() == 2
																	&& node5.getNodeName().startsWith("xsl:")) {
																if (node5.getAttributes().item(1).getNodeName()
																		.equals("select")) {
																	XWPFTableRow tableOneRowOne01 = tableOne2
																			.createRow();
																	tableOneRowOne01.getCell(0)
																			.setText(node2.getNodeName() + "/"
																					+ node3.getNodeName() + "/"
																					+ node4.getNodeName() + "/"
																					+ node5.getNodeName());
																	if(node4.getAttributes().item(1)!=null && node4.getAttributes().item(1).getNodeValue()!=null) {
																		tableOneRowOne01.getCell(1).setText(node4
																				.getAttributes().item(1).getNodeValue());
																	}
																}
															}
														}
														if (node5.hasChildNodes()) {
															NodeList nChildListf = node5.getChildNodes();
															for (int p = 0; p < nChildListf.getLength(); p++) {
																Node node6 = nChildListf.item(p);
																if ((!(node6.getNodeName()).equals(TEXT))
																		&& node6.getNodeName() != null) {
																	if (node6.hasAttributes()
																			&& !(node6.getNodeName()).equals(variable)
																			&& node6.getNodeName().startsWith("xsl:")) {
																		if (node6.getAttributes().item(0).getNodeName()
																				.equals("test")
																				|| (node6.getAttributes().item(0)
																						.getNodeName().equals("select")
																						&& !(node6.getNodeName().equals(
																								"xsl:value-of")))) {
																			XWPFTableRow tableOneRowOne03 = tableOne2
																					.createRow();
																			tableOneRowOne03.getCell(0)
																					.setText(node2.getNodeName() + "/"
																							+ node3.getNodeName() + "/"
																							+ node4.getNodeName() + "/"
																							+ node5.getNodeName() + "/"
																							+ node6.getNodeName());
																			tableOneRowOne03.getCell(1)
																					.setText(node6.getAttributes()
																							.item(0).getNodeValue());
																		}
																		if (node6.getAttributes().getLength() == 2
																				&& node6.getNodeName()
																						.startsWith("xsl:")) {
																			if (node6.getAttributes().item(1)
																					.getNodeName().equals("select")) {
																				XWPFTableRow tableOneRowOne01 = tableOne2
																						.createRow();
																				tableOneRowOne01.getCell(0)
																						.setText(node2.getNodeName()
																								+ "/"
																								+ node3.getNodeName()
																								+ "/"
																								+ node4.getNodeName()
																								+ "/"
																								+ node5.getNodeName()
																								+ "/"
																								+ node6.getNodeName());
																				tableOneRowOne01.getCell(1)
																						.setText(node6.getAttributes()
																								.item(1)
																								.getNodeValue());
																			}
																		}
																	}
																	if (node6.hasChildNodes()) {
																		NodeList nChildListg = node6.getChildNodes();
																		for (int q = 0; q < nChildListg
																				.getLength(); q++) {
																			Node node7 = nChildListg.item(q);
																			if ((!(node7.getNodeName()).equals(TEXT))
																					&& node7.getNodeName() != null) {
																				if (node7.getAttributes()
																						.getLength() != 0
																						&& !(node7.getNodeName())
																								.equals(variable)
																						&& node7.getNodeName()
																								.startsWith("xsl:")) {
																					if (node7.getAttributes().item(0)
																							.getNodeName()
																							.equals("test")
																							|| (node7.getAttributes()
																									.item(0)
																									.getNodeName()
																									.equals("select")
																									&& !(node7
																											.getNodeName()
																											.equals("xsl:value-of")))) {
																						XWPFTableRow tableOneRowOne03 = tableOne2
																								.createRow();
																						tableOneRowOne03.getCell(0)
																								.setText(node2
																										.getNodeName()
																										+ "/"
																										+ node3.getNodeName()
																										+ "/"
																										+ node4.getNodeName()
																										+ "/"
																										+ node5.getNodeName()
																										+ "/"
																										+ node6.getNodeName()
																										+ "/"
																										+ node7.getNodeName());
																						tableOneRowOne03.getCell(1)
																								.setText(node7
																										.getAttributes()
																										.item(0)
																										.getNodeValue());
																					}
																					if (node7.getAttributes()
																							.getLength() == 2
																							&& node7.getNodeName()
																									.startsWith(
																											"xsl:")) {
																						if (node7.getAttributes()
																								.item(1).getNodeName()
																								.equals("select")) {
																							XWPFTableRow tableOneRowOne01 = tableOne2
																									.createRow();
																							tableOneRowOne01.getCell(0)
																									.setText(node2
																											.getNodeName()
																											+ "/"
																											+ node3.getNodeName()
																											+ "/"
																											+ node4.getNodeName()
																											+ "/"
																											+ node5.getNodeName()
																											+ "/"
																											+ node6.getNodeName()
																											+ "/"
																											+ node7.getNodeName());
																							tableOneRowOne01.getCell(1)
																									.setText(node7
																											.getAttributes()
																											.item(1)
																											.getNodeValue());
																						}
																					}
																				}
																				if (node7.hasAttributes()
																						&& node7.getAttributes()
																								.item(0) != null) {
																					XWPFTableRow row4 = tableOne2
																							.createRow();
																					row4.getCell(0).setText(node2
																							.getNodeName() + "/"
																							+ node3.getNodeName() + "/"
																							+ node4.getNodeName() + "/"
																							+ node5.getNodeName() + "/"
																							+ node6.getNodeName());
																					row4.getCell(1).setText(node7
																							.getAttributes().item(0)
																							.getNodeValue());
																				}
																			}
																		}
																	} else if (node6.hasAttributes()
																			&& node6.getAttributes().item(0) != null) {
																		XWPFTableRow row4 = tableOne2.createRow();
																		row4.getCell(0)
																				.setText(node2.getNodeName() + "/"
																						+ node3.getNodeName() + "/"
																						+ node4.getNodeName() + "/"
																						+ node5.getNodeName());
																		row4.getCell(1).setText(node6.getAttributes()
																				.item(0).getNodeValue());
																	}
																}
															}
														} else if (node5.hasAttributes()
																&& node5.getAttributes().item(0) != null) {
															XWPFTableRow row4 = tableOne2.createRow();
															row4.getCell(0).setText(node2.getNodeName() + "/"
																	+ node3.getNodeName() + "/" + node4.getNodeName());
															row4.getCell(1).setText(
																	node5.getAttributes().item(0).getNodeValue());
														}
													}
												}
											} else if (node4.hasAttributes() && node4.getAttributes().item(0) != null) {
												XWPFTableRow row4 = tableOne2.createRow();
												row4.getCell(0)
														.setText(node2.getNodeName() + "/" + node3.getNodeName());
												row4.getCell(1).setText(node4.getAttributes().item(0).getNodeValue());
											}
										}
									}
								} else if (node3.hasAttributes() && node3.getAttributes().item(0) != null) {
									if (node3.getNodeName().equals(variable)) {
										XWPFTableRow row3 = tableOne2.createRow();
										row3.getCell(0).setText(node2.getNodeName() + "/"
												+ node3.getAttributes().item(0).getNodeValue());
										row3.getCell(1).setText(node3.getAttributes().item(1).getNodeValue());
									} else {
										XWPFTableRow row4 = tableOne2.createRow();
										row4.getCell(0).setText(node2.getNodeName());
										row4.getCell(1).setText(node3.getAttributes().item(0).getNodeValue());
									}
								}
							}
						}
					} else if (node2.hasAttributes() && node2.getAttributes().item(0) != null) {
						if(node2.getAttributes().getLength() == 2 && node2.getAttributes().item(1).getNodeName().equals("select")&& (node2.getNodeName()).equals(variable)) {
							XWPFTableRow row2 = tableOne2.createRow();
							row2.getCell(0).setText(node2.getAttributes().item(0).getNodeValue());
							row2.getCell(1).setText(node2.getAttributes().item(1).getNodeValue());
						}
						else {
						XWPFTableRow row2 = tableOne2.createRow();
						row2.getCell(0).setText(node2.getNodeName());
						row2.getCell(1).setText(node2.getAttributes().item(0).getNodeValue());
						}
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