
package org.geoserver.hibernate;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.orm.hibernate3.SessionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Hibernate utility class.
 * 
 * @author Justin Deoliveira, OpenGeo
 *
 */
public class HibUtil {

    public static void setUpSession(SessionFactory sessionFactory, boolean clearActiveTX) {
        if (clearActiveTX) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                try {
                    TransactionSynchronizationManager.unbindResource(sessionFactory);
                }
                catch(IllegalStateException e) {}
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
        
        setUpSession(sessionFactory);
    }
    public static void setUpSession(SessionFactory sessionFactory) {
        Session session = sessionFactory.openSession();
        
        TransactionSynchronizationManager.bindResource(sessionFactory, new SessionHolder(session));
        TransactionSynchronizationManager.initSynchronization();
    }
    
    public static void tearDownSession(SessionFactory sessionFactory, Throwable error) {
        Session session = sessionFactory.getCurrentSession();
        
        if (session.isOpen()) {
            session.close();
        }
        
        TransactionSynchronizationManager.unbindResource(sessionFactory);
        TransactionSynchronizationManager.clearSynchronization();
    }
}
