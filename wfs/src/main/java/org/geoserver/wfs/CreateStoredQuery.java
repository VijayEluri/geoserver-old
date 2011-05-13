/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs;

import net.opengis.wfs20.CreateStoredQueryResponseType;
import net.opengis.wfs20.CreateStoredQueryType;
import net.opengis.wfs20.StoredQueryDescriptionType;
import net.opengis.wfs20.Wfs20Factory;

/**
 * Web Feature Service CreateStoredQuery operation.
 *
 * @author Justin Deoliveira, OpenGeo
 *
 * @version $Id$
 */
public class CreateStoredQuery {

    /** service config */
    WFSInfo wfs;

    /** stored query provider */
    StoredQueryProvider storedQueryProvider;

    public CreateStoredQuery(WFSInfo wfs, StoredQueryProvider storedQueryProvider) {
        this.wfs = wfs;
        this.storedQueryProvider = storedQueryProvider;
    }
    
    public CreateStoredQueryResponseType run(CreateStoredQueryType request) throws WFSException {
        for (StoredQueryDescriptionType sqd : request.getStoredQueryDefinition()) {
            validateStoredQuery(sqd);
            
            try {
                storedQueryProvider.createStoredQuery(sqd);
            }
            catch(Exception e) {
                throw new WFSException("Error occured creating stored query", e);
            }
        }

        Wfs20Factory factory = Wfs20Factory.eINSTANCE;
        CreateStoredQueryResponseType response = factory.createCreateStoredQueryResponseType();
        response.setStatus("OK");
        return response;
    }

    void validateStoredQuery(StoredQueryDescriptionType sq) throws WFSException {
        if (sq.getQueryExpressionText().isEmpty()) {
            throw new WFSException("Stored query does not specify any queries");
        }
        String language = sq.getQueryExpressionText().get(0).getLanguage();
        for (int i = 1; i < sq.getQueryExpressionText().size(); i++) {
            if (!language.equals(sq.getQueryExpressionText().get(i).getLanguage())) {
                throw new WFSException("Stored query specifies queries with multiple languages. " +
                    "Not supported");
            }
        }
    }
}
