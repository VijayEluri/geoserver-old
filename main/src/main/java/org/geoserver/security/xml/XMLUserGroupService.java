/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.security.xml;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.io.FileUtils;
import org.geoserver.security.GeoserverUserGroupStore;
import org.geoserver.security.config.FileBasedSecurityServiceConfig;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.geoserver.security.config.SecurityUserGroupServiceConfig;
import org.geoserver.security.impl.AbstractUserGroupService;
import org.geoserver.security.impl.GeoserverUser;
import org.geoserver.security.impl.GeoserverUserGroup;
import org.geoserver.security.impl.Util;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author christian
 *
 */
public class XMLUserGroupService extends AbstractUserGroupService {
            
    static Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.geoserver.security.xml");
    protected DocumentBuilder builder;
    protected File userFile;
    /**
     * Validate against schema on load/store,
     * default = true;
     */
    private boolean validatingXMLSchema = true;


    public XMLUserGroupService() throws IOException{
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setIgnoringComments(true);
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IOException(e);
        } 
    }

    @Override
    public void initializeFromConfig(SecurityNamedServiceConfig config) throws IOException {

        this.name=config.getName();
        validatingXMLSchema=false;
        passwordEncoderName=((SecurityUserGroupServiceConfig)config).getPasswordEncoderName();
        passwordValidatorName=((SecurityUserGroupServiceConfig)config).getPasswordPolicyName();

        if (config instanceof XMLSecurityServiceConfig) {
            validatingXMLSchema =((XMLSecurityServiceConfig) config).isValidating();
            // copy schema file 
            File xsdFile = new File(getConfigRoot(), XMLConstants.FILE_UR_SCHEMA);
            if (xsdFile.exists()==false) {
                FileUtils.copyURLToFile(getClass().getResource(XMLConstants.FILE_UR_SCHEMA), xsdFile);
            }            

        }
        
        if (config instanceof FileBasedSecurityServiceConfig) {
            String fileName = ((FileBasedSecurityServiceConfig) config).getFileName();
            userFile = new File(fileName);
            if (userFile.isAbsolute()==false) {
                userFile= new File(getConfigRoot(), fileName);
            } 
            if (userFile.exists()==false) {
                FileUtils.copyURLToFile(getClass().getResource("usersTemplate.xml"), userFile);
            }
        } else {
            throw new IOException("Cannot initialize from " +config.getClass().getName());
        }        
        deserialize();
    }

    @Override
    public boolean canCreateStore() {
        return true;
    }

    @Override
    public GeoserverUserGroupStore createStore() throws IOException {
        XMLUserGroupStore store = new XMLUserGroupStore();
        store.initializeFromService(this);
        return store;
    }
    
    public boolean isValidatingXMLSchema() {
        return validatingXMLSchema;
    }

    public void setValidatingXMLSchema(boolean validatingXMLSchema) {
        this.validatingXMLSchema = validatingXMLSchema;
    }



    /* (non-Javadoc)
     * @see org.geoserver.security.impl.AbstractUserGroupService#deserialize()
     */
    @Override
    protected void deserialize() throws IOException {
        
        try {
            
            Document doc=null;
            try {
                doc = builder.parse(new FileInputStream(userFile));
            } catch (SAXException e) {
                throw new IOException(e);
            }
            
            if (isValidatingXMLSchema()) {
                XMLValidator.Singleton.validateUserGroupRegistry(doc);
            }

            
            XPathExpression expr = XMLXpathFactory.Singleton.getVersionExpressionUR();
            String versionNummer = expr.evaluate(doc);
            UserGroupXMLXpath xmlXPath = XMLXpathFactory.Singleton.getUserGroupXMLXpath(versionNummer);

            clearMaps();
            
            NodeList userNodes = (NodeList) xmlXPath.getUserListExpression().evaluate(doc,XPathConstants.NODESET);
            for ( int i=0 ; i <userNodes.getLength();i++) {
                Node userNode = userNodes.item(i);
                boolean userEnabled = Util.convertToBoolean(xmlXPath.getUserEnabledExpression().evaluate(userNode),true);
                String userPassword = xmlXPath.getUserPasswordExpression().evaluate(userNode);
                String userName = xmlXPath.getUserNameExpression().evaluate(userNode);
                NodeList propertyNodes = (NodeList) xmlXPath.getUserPropertiesExpression().evaluate(userNode,XPathConstants.NODESET);
                Properties userProps = new Properties();
                for ( int j=0 ; j <propertyNodes.getLength();j++) {
                    Node propertyNode = propertyNodes.item(j);
                    String propertyName = xmlXPath.getPropertyNameExpression().evaluate(propertyNode);
                    String propertyValue = xmlXPath.getPropertyValueExpression().evaluate(propertyNode);
                    userProps.put(propertyName, propertyValue);
                }                                
                GeoserverUser user=createUserObject(userName, userPassword, userEnabled);
                helper.userMap.put(user.getUsername(), user);
                user.getProperties().clear();       // set properties
                for (Object key: userProps.keySet()) {
                    user.getProperties().put(key, userProps.get(key));
                }
            }
                        
            NodeList groupNodes = (NodeList) xmlXPath.getGroupListExpression().evaluate(doc,XPathConstants.NODESET);
            for ( int i=0 ; i <groupNodes.getLength();i++) {
                Node groupNode = groupNodes.item(i);
                String groupName = xmlXPath.getGroupNameExpression().evaluate(groupNode);
                boolean groupEnabled = Util.convertToBoolean(xmlXPath.getGroupEnabledExpression().evaluate(groupNode),true);
                GeoserverUserGroup group= createGroupObject(groupName, groupEnabled);
                helper.groupMap.put(groupName, group);
                NodeList memberNodes = (NodeList) xmlXPath.getGroupMemberListExpression().evaluate(groupNode,XPathConstants.NODESET);
                for ( int j=0 ; j <memberNodes.getLength();j++) {
                    Node memberNode = memberNodes.item(j);
                    String memberName = xmlXPath.getGroupMemberNameExpression().evaluate(memberNode);
                    GeoserverUser member=helper.userMap.get(memberName);
                    
                    SortedSet<GeoserverUser> members=helper.group_userMap.get(group);
                    if (members==null) {
                        members=new TreeSet<GeoserverUser>();
                        helper.group_userMap.put(group, members);
                    }
                    members.add(member);
                    
                    SortedSet<GeoserverUserGroup> userGroups=helper.user_groupMap.get(member);
                    if (userGroups==null) {
                        userGroups=new TreeSet<GeoserverUserGroup>();
                        helper.user_groupMap.put(member, userGroups);
                    }
                    userGroups.add(group);
                    
                }    
            }
        } catch (XPathExpressionException ex) {
            throw new IOException(ex);
        }
                    
    }
    

    

}
