package com.knowledgegraph.assistant.host;

import com.knowledgegraph.assistant.host.demo.AssistantWebsiteDemoConfig;
import org.springframework.jdbc.datasource.AbstractDataSource;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/** Lazy website reader: verifies live identity and exact table SELECT grants on every opened connection. */
public final class AssistantWebsiteDemoReadDataSource extends AbstractDataSource {
    public static final Set<String> TABLES=Set.of("knowledge_libraries","library_aliases","library_nodes","documents",
            "document_metadata","document_tags","document_topic_assignments","document_units","document_chunks",
            "knowledge_nodes","node_aliases","knowledge_edges","node_evidence","edge_evidence");
    static final String URL="jdbc:mysql://127.0.0.1:3306/tupu_kg";
    @FunctionalInterface interface Connector {Connection connect(String url,Properties properties)throws SQLException;}
    private final AssistantWebsiteDemoConfig config;
    private final Connector connector;
    private final Clock clock;
    private volatile boolean failed;
    private boolean verified;
    private Instant checked=Instant.MIN;
    private String fingerprint;
    public AssistantWebsiteDemoReadDataSource(AssistantWebsiteDemoConfig config){this(config,DriverManager::getConnection,Clock.systemUTC());}
    AssistantWebsiteDemoReadDataSource(AssistantWebsiteDemoConfig config,Connector connector,Clock clock){this.config=config;this.connector=connector;this.clock=clock;}
    /** No synthetic evidence path. The cache lasts at most 60s; new business connections always reverify. */
    synchronized String verify() {
        if(failed)throw AssistantWebsiteDemoConfig.rejected();
        if(!verified || !clock.instant().isBefore(checked.plusSeconds(60))) {
            try(Connection ignored=getConnection()) {}catch(SQLException ex){throw AssistantWebsiteDemoConfig.rejected();}
        }
        return fingerprint;
    }
    synchronized boolean verifiedPermission(){try{verify();return true;}catch(RuntimeException ex){return false;}}
    @Override public synchronized Connection getConnection()throws SQLException {
        if(failed)throw rejected(null);Connection raw=null;
        try {
            Properties p=new Properties();p.setProperty("user",config.readerAccount());p.setProperty("password",config.readerPassword());
            p.setProperty("connectTimeout","3000");p.setProperty("socketTimeout","4000");
            for(String key:List.of("allowMultiQueries","autoReconnect","autoReconnectForPools","allowLoadLocalInfile","allowUrlInLocalInfile"))p.setProperty(key,"false");
            raw=connector.connect(URL,p);if(raw==null)throw rejected(null);
            if(!"MySQL".equals(raw.getMetaData().getDatabaseProductName()) || !"tupu_kg".equals(raw.getCatalog())
                    || !config.readerAccount().equals(account(raw.getMetaData().getUserName())))throw rejected(null);
            raw.setReadOnly(true);raw.setNetworkTimeout(Runnable::run,4000);
            List<String> grants=new ArrayList<>();String identity;
            try(Statement s=raw.createStatement()) {
                s.setQueryTimeout(2);s.execute("SET SESSION max_execution_time = 1500");
                try(ResultSet r=s.executeQuery("SELECT DATABASE(), CURRENT_USER(), VERSION()")) {
                    if(!r.next() || !"tupu_kg".equals(r.getString(1)) || !config.readerAccount().equals(account(r.getString(2))) || r.getString(3)==null)throw rejected(null);
                    identity=r.getString(2);String host=identity.substring(identity.indexOf('@')+1);
                    if(!Set.of("localhost","127.0.0.1").contains(host) || r.next())throw rejected(null);
                }
                try(ResultSet r=s.executeQuery("SHOW GRANTS FOR CURRENT_USER()")) {
                    while(r.next()) {if(grants.size()>=64)throw rejected(null);grants.add(r.getString(1));}
                }
            }
            verifyGrants(grants,identity);
            fingerprint=AssistantReadAcceptanceConfig.sha256((identity+"\ntupu_kg\n"+String.join("\n",new TreeSet<>(grants))).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            verified=true;checked=clock.instant();return connection(raw);
        }catch(Exception ex) {
            failed=true;verified=false;if(raw!=null)try{raw.close();}catch(Exception ignored){}
            throw rejected(ex);
        }
    }
    private static String account(String s){return s==null?"":s.split("@",2)[0];}
    private void verifyGrants(List<String> grants,String identity)throws SQLException {
        Set<String> tables=new HashSet<>();boolean usage=false;
        String[] who=identity.split("@",2);
        String principal="(?:`"+Pattern.quote(who[0])+"`|'"+Pattern.quote(who[0])+"')@(?:`"+Pattern.quote(who[1])+"`|'"+Pattern.quote(who[1])+"')";
        Pattern read=Pattern.compile("^GRANT SELECT ON `tupu_kg`\\.`([a-z_]+)` TO "+principal+"$");
        Pattern none=Pattern.compile("^GRANT USAGE ON \\*\\.\\* TO "+principal+"$");
        for(String grant:grants) {
            if(grant==null)throw rejected(null);Matcher m=read.matcher(grant);
            if(m.matches()) {if(!TABLES.contains(m.group(1)) || !tables.add(m.group(1)))throw rejected(null);}
            else if(none.matcher(grant).matches() && !usage)usage=true;
            else throw rejected(null); // includes roles, database/global grants, columns, writes, GRANT OPTION
        }
        if(!tables.equals(TABLES))throw rejected(null);
    }
    private Connection connection(Connection raw) {
        return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,m,a)-> {
            String n=m.getName();if(n.equals("toString"))return "WebsiteReadConnection[restricted]";
            if(Set.of("unwrap","prepareCall","createStatement","setSchema","setClientInfo").contains(n)
                    || n.equals("setReadOnly") && Boolean.FALSE.equals(a[0]) || n.equals("setCatalog") && !"tupu_kg".equals(a[0]))throw rejected(null);
            if(n.equals("isWrapperFor"))return false;
            if(n.equals("setNetworkTimeout")){int v=(Integer)a[1];if(v<0)throw rejected(null);a=a.clone();a[1]=v==0?4000:Math.min(v,4000);}
            if(n.equals("getMetaData"))return metadata(raw.getMetaData());
            if(n.equals("prepareStatement")) {
                PreparedStatement s=(PreparedStatement)call(raw,m,a);
                try{s.setQueryTimeout(2);}catch(Exception e){try{s.close();}catch(Exception ignored){}throw rejected(e);}
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,sm,sa)-> {
                    String op=sm.getName();if(op.equals("toString"))return "WebsiteReadStatement[restricted]";
                    if(op.equals("unwrap"))throw rejected(null);if(op.equals("isWrapperFor"))return false;if(op.equals("getConnection"))return proxy;
                    if(op.startsWith("execute") && failed)throw rejected(null);
                    if(op.equals("setQueryTimeout")){int v=(Integer)sa[0];if(v<0)throw rejected(null);sa=new Object[]{v==0?2:Math.min(v,2)};}
                    Object out=call(s,sm,sa);return out instanceof ResultSet rs?result(rs):out;
                });
            }
            return call(raw,m,a);
        });
    }
    private DatabaseMetaData metadata(DatabaseMetaData raw) {
        return (DatabaseMetaData)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DatabaseMetaData.class},(p,m,a)-> {
            if(Set.of("getConnection","unwrap").contains(m.getName()))throw rejected(null);
            if(m.getName().equals("isWrapperFor"))return false;if(m.getName().equals("toString"))return "WebsiteReadMetadata[restricted]";
            Object out=call(raw,m,a);return out instanceof ResultSet rs?result(rs):out;
        });
    }
    private ResultSet result(ResultSet raw) {
        return (ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)-> {
            if(Set.of("getStatement","unwrap").contains(m.getName()) || m.getName().startsWith("update") || Set.of("insertRow","deleteRow").contains(m.getName()))throw rejected(null);
            if(m.getName().equals("isWrapperFor"))return false;if(m.getName().equals("toString"))return "WebsiteReadResult[restricted]";return call(raw,m,a);
        });
    }
    private static Object call(Object target,Method method,Object[] args)throws Throwable {
        try{return method.invoke(target,args);}catch(InvocationTargetException e){if(e.getCause() instanceof SQLException)throw rejected(e.getCause());throw e.getCause();}
    }
    static SQLException rejected(Throwable e){return e instanceof SQLException s?new SQLException("Website reader unavailable",s.getSQLState(),s.getErrorCode()):new SQLException("Website reader unavailable");}
    @Override public Connection getConnection(String user,String password)throws SQLException{throw rejected(null);}
    @Override public void setLogWriter(PrintWriter writer)throws SQLException{if(writer!=null)throw rejected(null);}
    @Override public PrintWriter getLogWriter(){return null;}
    @Override public void setLoginTimeout(int seconds)throws SQLException{if(seconds<1 || seconds>3)throw rejected(null);}
    @Override public int getLoginTimeout(){return 3;}
    @Override public <T>T unwrap(Class<T> type)throws SQLException{throw rejected(null);}
    @Override public boolean isWrapperFor(Class<?> type){return false;}
    @Override public String toString(){return "WebsiteDemoReadDataSource[restricted]";}
}
