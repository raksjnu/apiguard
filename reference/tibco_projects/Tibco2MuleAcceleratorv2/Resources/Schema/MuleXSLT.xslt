<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"  
xmlns:pfx="http://www.mulesoft.org/schema/mule/core" xmlns:pfx2="http://www.tibco.com/schemas/tibco2mulePOC/Raks/report.xsd" xmlns:ns="http://www.tibco.com/pe/WriteToLogActivitySchema" xmlns:pfx3="http://ibm.com/raks/tibco2mule/accelerator/Resources/Schema/Tibco/LocationDetails.xsd" xmlns:pfx4="http://www.tibco.com/schemas/Tibco2MuleAccelerator/Resources/Schema/Tibco/FileInfo.xsd" xmlns:pfx5="http://www.mulesoft.org/schema/mule/MuleFlow" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:tib="http://www.tibco.com/bw/xslt/custom-functions" xmlns:ns2="http://www.tibco.com/namespaces/tnt/plugins/file" xmlns:ns1="http://www.tibco.com/Resources/Schema/FlowSchema/FlowSchema.xsd" xmlns:ns4="http://ibm.com/raks/tibco2mule/accelerator/Resources/Schema/Tibco/All.xsd" xmlns:ns3="http://www.tibco.com/pe/DeployedVarsType" xmlns:ns6="http://www.tibco.com/schemas/Tibco2MuleAccelerator/Resources/Schema/Tibco/ProjectInfo.xsd" xmlns:cxf="http://www.mulesoft.org/schema/mule/cxf" xmlns:ns5="http://ibm.com/raks/tibco2mule/accelerator/Resources/Schema/Tibco/ProcessInfo.xsd" xmlns:jms="http://www.mulesoft.org/schema/mule/jms" xmlns:ns8="http://www.tibco.com/namespaces/tnt/plugins/file/v2" xmlns:dw="http://www.mulesoft.org/schema/mule/ee/dw" xmlns:file="http://www.mulesoft.org/schema/mule/file" xmlns:ns7="http://ibm.com/raks/tibco2mule/accelerator/Resources/Schema/Tibco/GVInfo.xsd" xmlns:ns9="http://ibm.com/raks/tibco2mule/accelerator/Resources/Schema/Checklist.xsd" xmlns:ns10="http://www.springframework.org/schema/beans" xmlns:ws="http://www.mulesoft.org/schema/mule/ws" xmlns:sap="http://www.mulesoft.org/schema/mule/sap" xmlns:ftp="http://www.mulesoft.org/schema/mule/ftp" xmlns:pd="http://xmlns.tibco.com/bw/process/2003" xmlns:http="http://www.mulesoft.org/schema/mule/http" xmlns:doc="http://www.mulesoft.org/schema/mule/documentation" xmlns:db="http://www.mulesoft.org/schema/mule/db"
>
<xsl:output  omit-xml-declaration="yes" />
<xsl:strip-space elements="*" />


   <xsl:template match="/">
    <xsl:for-each select="node()/*">
	<xsl:sort select="@posX" />
  	    <xsl:copy-of select="." />
	<xsl:text>~</xsl:text>
    </xsl:for-each>
  </xsl:template>

</xsl:stylesheet>	