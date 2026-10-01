package com.knowledgegraph.assistant.host;

import org.springframework.jdbc.datasource.AbstractDataSource;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.sql.*;
import java.time.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/** Lazy connection/budget adapter BELOW the existing SELECT and reader-identity guard. No SQL rewrite. */
public final class AssistantReadAcceptanceDataSource extends AbstractDataSource {
    @FunctionalInterface interface Connector {Connection connect(String url,Properties properties)throws SQLException;}
    static final String URL="jdbc:mysql://127.0.0.1:33078/kg_assistant_i2_20260927_r01";
    static final String SESSION_BUDGET="SET SESSION max_execution_time = 1500";
    static final String INSTANCE_UUID="SELECT @@server_uuid";
    private final AssistantReadAcceptanceConfig config;
    private final AssistantReadAcceptanceEvidence evidence;
    private final Connector connector;
    private final Clock clock;
    private final AtomicBoolean usable=new AtomicBoolean(true);
    AssistantReadAcceptanceDataSource(AssistantReadAcceptanceConfig config,AssistantReadAcceptanceEvidence evidence) {
        this(config,evidence,DriverManager::getConnection,Clock.systemUTC());
    }
    /** Package-private fake JDBC seam; main always uses DriverManager and the fixed literal URL. */
    AssistantReadAcceptanceDataSource(AssistantReadAcceptanceConfig config,AssistantReadAcceptanceEvidence evidence,Connector connector,Clock clock) {
        this.config=config;this.evidence=evidence;this.connector=connector;this.clock=clock;
    }
    boolean authorityValid(){return usable.get() && evidence.valid(clock.instant());}
    @Override public Connection getConnection()throws SQLException {
        if(!authorityValid())throw rejected();
        Connection raw=null;
        try {
            Properties p=new Properties();p.setProperty("user",config.account());p.setProperty("password",config.password());
            p.setProperty("connectTimeout","3000");p.setProperty("socketTimeout","4000");
            p.setProperty("allowMultiQueries","false");p.setProperty("autoReconnect","false");p.setProperty("autoReconnectForPools","false");
            p.setProperty("allowLoadLocalInfile","false");p.setProperty("allowUrlInLocalInfile","false");
            raw=connector.connect(URL,p);
            if(raw==null || !authorityValid())throw rejected();
            DatabaseMetaData metadata=raw.getMetaData();
            if(!"MySQL".equals(metadata.getDatabaseProductName()) || !AssistantReadAcceptanceConfig.VERSION.equals(metadata.getDatabaseProductVersion())
                    || metadata.getUserName()==null || !config.account().equals(metadata.getUserName().split("@",2)[0])
                    || !AssistantReadAcceptanceConfig.DATABASE.equals(raw.getCatalog()))throw rejected();
            raw.setReadOnly(true);raw.setNetworkTimeout(Runnable::run,4000);
            // The only initialization SQL: the historically approved per-session SELECT time budget.
            try(Statement budget=raw.createStatement()) {
                budget.setQueryTimeout(2);budget.execute(SESSION_BUDGET);
                // Fixed server identity read, never caller-provided init SQL or a business query.
                try(ResultSet identity=budget.executeQuery(INSTANCE_UUID)) {
                    if(!identity.next() || !AssistantReadAcceptanceConfig.UUID.equals(identity.getString(1)) || identity.next())throw rejected();
                }
            }
            if(!authorityValid())throw rejected();
            return connection(raw);
        }catch(Exception ex) {
            usable.set(false);
            if(raw!=null)try{raw.close();}catch(Exception ignored){}
            // Drivers can echo connection properties. Retain only their safe technical error identity.
            throw rejected(ex);
        }
    }
    private Connection connection(Connection raw) {
        return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)-> {
            String name=method.getName();
            if(name.equals("toString"))return "AcceptanceReadConnection[restricted]";
            if(name.equals("unwrap") || name.equals("prepareCall") || name.equals("createStatement")
                    || name.equals("setReadOnly") && Boolean.FALSE.equals(args[0]) || name.equals("setSchema")
                    || name.equals("setCatalog") && !AssistantReadAcceptanceConfig.DATABASE.equals(args[0]))throw rejected();
            if(name.equals("setClientInfo"))throw new SQLClientInfoException();
            if(name.equals("isWrapperFor"))return false;
            if(name.equals("setNetworkTimeout")) {
                int requested=(Integer)args[1];if(requested<0)throw rejected();
                args=args.clone();args[1]=requested==0?4000:Math.min(requested,4000);
            }
            if(name.equals("getMetaData")) {
                DatabaseMetaData metadata=(DatabaseMetaData)call(raw,method,args);
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DatabaseMetaData.class},(m,meth,a)-> {
                    if(meth.getName().equals("getConnection") || meth.getName().equals("unwrap"))throw rejected();
                    if(meth.getName().equals("isWrapperFor"))return false;
                    if(meth.getName().equals("toString"))return "AcceptanceReadMetadata[restricted]";
                    return call(metadata,meth,a);
                });
            }
            if(name.equals("prepareStatement")) {
                if(!authorityValid())throw rejected();
                PreparedStatement statement=(PreparedStatement)call(raw,method,args);
                try{statement.setQueryTimeout(2);}catch(Exception ex){try{statement.close();}catch(Exception ignored){}throw rejected(ex);}
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)-> {
                    String operation=m.getName();
                    if(operation.equals("toString"))return "AcceptanceReadStatement[restricted]";
                    if(operation.equals("unwrap"))throw rejected();if(operation.equals("isWrapperFor"))return false;
                    if(operation.equals("getConnection"))return proxy;
                    if(operation.startsWith("execute") && !authorityValid())throw rejected();
                    if(operation.equals("setQueryTimeout")) {
                        int requested=(Integer)a[0];if(requested<0)throw rejected();
                        a=new Object[]{requested==0?2:Math.min(requested,2)};
                    }
                    return call(statement,m,a);
                });
            }
            return call(raw,method,args);
        });
    }
    private static Object call(Object target,Method method,Object[] args)throws Throwable {
        try{return method.invoke(target,args);}catch(InvocationTargetException ex) {
            if(ex.getCause() instanceof SQLException)throw rejected(ex.getCause());throw ex.getCause();
        }
    }
    static SQLException rejected(){return new SQLException("Acceptance reader unavailable or operation rejected");}
    static SQLException rejected(Throwable failure) {
        if(failure instanceof SQLException sql)
            return new SQLException("Acceptance reader unavailable or operation rejected",sql.getSQLState(),sql.getErrorCode());
        return rejected();
    }
    @Override public Connection getConnection(String user,String password)throws SQLException {throw rejected();}
    @Override public void setLoginTimeout(int seconds)throws SQLException {if(seconds<1 || seconds>3)throw rejected();}
    @Override public int getLoginTimeout(){return 3;}
    @Override public void setLogWriter(PrintWriter writer)throws SQLException {if(writer!=null)throw rejected();}
    @Override public PrintWriter getLogWriter(){return null;}
    @Override public <T>T unwrap(Class<T> type)throws SQLException {throw rejected();}
    @Override public boolean isWrapperFor(Class<?> type){return false;}
    @Override public String toString(){return "AssistantReadAcceptanceDataSource[restricted]";}
}
