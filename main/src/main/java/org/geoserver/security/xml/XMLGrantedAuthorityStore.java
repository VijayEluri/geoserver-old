/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.security.xml;

import static org.geoserver.security.xml.XMLConstants.*;

import java.io.File;
import java.io.IOException;
import java.util.SortedSet;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.geoserver.security.GeoserverGrantedAuthorityService;
import org.geoserver.security.file.LockFile;
import org.geoserver.security.impl.AbstractGrantedAuthorityStore;
import org.geoserver.security.impl.GeoserverGrantedAuthority;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class XMLGrantedAuthorityStore extends AbstractGrantedAuthorityStore {

    static Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.geoserver.security.xml");
    protected File roleFile;
    protected LockFile lockFile = null;
    /**
     * Validate against schema on load/store,
     * default = true;
     */
    private boolean validatingXMLSchema = true;
    
    public XMLGrantedAuthorityStore(String name) {
        super(name);
    }
    
    public boolean isValidatingXMLSchema() {
        return validatingXMLSchema;
    }

    public void setValidatingXMLSchema(boolean validatingXMLSchema) {
        this.validatingXMLSchema = validatingXMLSchema;
    }

    
    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverGrantedAuthorityStore#initializeFromServer(org.geoserver.security.GeoserverGrantedAuthorityService)
     */
    public void initializeFromService(GeoserverGrantedAuthorityService service) throws IOException {                        
        this.roleFile=((XMLGrantedAuthorityService)service).roleFile;
        this.validatingXMLSchema=((XMLGrantedAuthorityService)service).isValidatingXMLSchema();
        super.initializeFromService(service);
                
    }

    
    @Override
    protected void serialize() throws IOException {
        
        
        DocumentBuilder builder=null;
        try {
            builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e1) {
            throw new IOException(e1);
        }
        Document doc =builder.newDocument();
        
        Element rolereg = doc.createElement(E_ROLEREGISTRY_RR);
        doc.appendChild(rolereg);
        rolereg.setAttribute(javax.xml.XMLConstants.XMLNS_ATTRIBUTE, NS_RR);
        rolereg.setAttribute(A_VERSION_RR, VERSION_RR_1_0);
        
        Element rolelist = doc.createElement(E_ROLELIST_RR);
        rolereg.appendChild(rolelist);
        
        for (GeoserverGrantedAuthority roleObject : roleMap.values()) {
            Element role = doc.createElement(E_ROLE_RR);
            rolelist.appendChild(role);
            role.setAttribute(A_ROLEID_RR, roleObject.getAuthority());
            GeoserverGrantedAuthority parentObject = role_parentMap.get(roleObject);
            if (parentObject!=null) {
                role.setAttribute(A_PARENTID_RR, parentObject.getAuthority());
            }            
            for (Object key: roleObject.getProperties().keySet()) {
                Element property = doc.createElement(E_PROPERTY_RR);
                role.appendChild(property);
                property.setAttribute(A_PROPERTY_NAME_RR, key.toString());
                property.setTextContent(roleObject.getProperties().getProperty(key.toString()));
            }
        }
        
        Element userList = doc.createElement(E_USERLIST_RR);
        rolereg.appendChild(userList);
        for (String userName: user_roleMap.keySet()) {
            Element userroles = doc.createElement(E_USERROLES_RR);
            userList.appendChild(userroles);
            userroles.setAttribute(A_USERNAME_RR, userName);
            SortedSet<GeoserverGrantedAuthority> roleObjects =  user_roleMap.get(userName);
            for (GeoserverGrantedAuthority roleObject: roleObjects) {
                Element ref = doc.createElement(E_ROLEREF_RR);
                userroles.appendChild(ref);
                ref.setAttribute(A_ROLEREFID_RR,roleObject.getAuthority());
            }
        }
        
        Element groupList = doc.createElement(E_GROUPLIST_RR);
        rolereg.appendChild(groupList);
        
        for (String groupName: group_roleMap.keySet()) {
            Element grouproles = doc.createElement(E_GROUPROLES_RR);
            groupList.appendChild(grouproles);
            grouproles.setAttribute(A_GROUPNAME_RR, groupName);
            SortedSet<GeoserverGrantedAuthority> roleObjects =  group_roleMap.get(groupName);
            for (GeoserverGrantedAuthority roleObject: roleObjects) {
                Element ref = doc.createElement(E_ROLEREF_RR);
                grouproles.appendChild(ref);
                ref.setAttribute(A_ROLEREFID_RR,roleObject.getAuthority());
            }
        }
       
        // serialize the dom
        try {
//            TODO, wait for JAVA 6
//            if (isValidatingXMLSchema()) {
//                XMLValidator.Singleton.validateGrantedAuthorityRegistry(doc);
//            }
            
            Source source = new DOMSource(doc);

            // Prepare the output file            
            Result result = new StreamResult(roleFile);

            // Write the DOM document to the file
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.transform(source, result);
            
        } catch (Exception e) {
            throw new IOException(e);
        }
        

    }

    @Override
    protected void deserialize() throws IOException {
        super.deserialize();
        releaseLock();
    }


    
    @Override
    public String toString() {
        return getName();
    }
            
    protected void ensureLock() throws IOException {
        if (lockFile!=null) return; // we have one
        lockFile=new LockFile(roleFile);
        try {
            lockFile.writeLock();
        } catch (IOException ex) { // cannot obtain lock
            lockFile=null; // assert lockFile == null
            throw ex; // throw again
        }
    }
    
    protected void releaseLock()  {
        if (lockFile==null) return; // we have none        
        lockFile.writeUnLock();
        lockFile=null;
    }


    @Override
    public void addGrantedAuthority(GeoserverGrantedAuthority role) throws IOException {
        ensureLock();
        super.addGrantedAuthority(role);
    }

    @Override
    public void updateGrantedAuthority(GeoserverGrantedAuthority role) throws IOException {
        ensureLock();
        super.updateGrantedAuthority(role);
    }

    @Override
    public boolean removeGrantedAuthority(GeoserverGrantedAuthority role) throws IOException {
        ensureLock();
        return super.removeGrantedAuthority(role);
    }

    @Override
    public void store() throws IOException {
       ensureLock();
       super.store();       
       releaseLock();
    }

    @Override
    public void disAssociateRoleFromGroup(GeoserverGrantedAuthority role, String groupname)
            throws IOException {
        ensureLock();
        super.disAssociateRoleFromGroup(role, groupname);
    }

    @Override
    public void associateRoleToGroup(GeoserverGrantedAuthority role, String groupname)
            throws IOException {
        ensureLock();
        super.associateRoleToGroup(role, groupname);
    }

    @Override
    public void associateRoleToUser(GeoserverGrantedAuthority role, String username)
            throws IOException {
        ensureLock();
        super.associateRoleToUser(role, username);
    }

    @Override
    public void disAssociateRoleFromUser(GeoserverGrantedAuthority role, String username)
            throws IOException {
        ensureLock();
        super.disAssociateRoleFromUser(role, username);
    }

    @Override
    public void setParentRole(GeoserverGrantedAuthority role, GeoserverGrantedAuthority parentRole)
            throws IOException {
        ensureLock();
        super.setParentRole(role, parentRole);
    }

    @Override
    public void clear() throws IOException {
        ensureLock();
        super.clear();
    }
}
