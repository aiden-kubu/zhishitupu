package com.knowledgegraph.assistant.host;

import com.knowledgegraph.common.ApiResponse;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.Set;

/** Local single-owner boundary. No forwarded identity, cross-origin API or public binding. */
final class AssistantReadBoundaryFilter extends OncePerRequestFilter {
    private static final Set<String> LOCAL=Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1");
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Content-Type-Options","nosniff");
        String code=null;
        if(!LOCAL.contains(request.getRemoteAddr()) || !Set.of("localhost","127.0.0.1","::1","[::1]").contains(request.getServerName()))code="ACCESS_CONTEXT_UNAVAILABLE";
        String origin=request.getHeader("Origin");
        if(origin!=null) {
            try {URI uri=URI.create(origin);
                int port=uri.getPort()==-1?("https".equals(uri.getScheme())?443:80):uri.getPort();
                if(!request.getScheme().equals(uri.getScheme())||!request.getServerName().equals(uri.getHost())||request.getServerPort()!=port)code="ACCESS_CONTEXT_UNAVAILABLE";
            }catch(RuntimeException ex){code="ACCESS_CONTEXT_UNAVAILABLE";}
        }
        if(request.getHeader("Forwarded")!=null||request.getHeader("X-Forwarded-For")!=null||"cross-site".equals(request.getHeader("Sec-Fetch-Site")))code="ACCESS_CONTEXT_UNAVAILABLE";
        if(code!=null) {response.setStatus(401);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
            response.getWriter().write(HostJson.JSON.writeValueAsString(ApiResponse.error(code,code)));return;}
        if("GET".equals(request.getMethod()) && (request.getContentLengthLong()>0 || request.getInputStream().read()!=-1)) {
            response.setStatus(400);response.setContentType("application/json");response.setCharacterEncoding("UTF-8");
            response.getWriter().write(HostJson.JSON.writeValueAsString(ApiResponse.error("INVALID_ARGUMENT","INVALID_ARGUMENT")));return;
        }
        chain.doFilter(request,response);
    }
}
