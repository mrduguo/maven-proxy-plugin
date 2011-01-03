package org.duguo.maven.plugins.proxy.jetty;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.duguo.maven.plugins.proxy.HttpProxyMojo;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.IO;

public class DownloadRequestHandler extends AbstractHandler{
	private HttpProxyMojo proxyMojo;
	public DownloadRequestHandler(HttpProxyMojo proxyMojo) {
		this.proxyMojo=proxyMojo;
	}

	public void handle(String target, Request baseRequest,
			HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		File resource =proxyMojo.resolveArtifact(target);
		if(resource!=null){
			proxyMojo.getLog().debug("Responding with file: "+resource.getAbsolutePath());
			FileInputStream fileInputStream=new FileInputStream(resource);
			IO.copy(fileInputStream, response.getOutputStream());
			fileInputStream.close();
			if(resource.getName().endsWith(".tmp.index.html")){
				resource.delete();
			}
			proxyMojo.getLog().info("200 "+target);
		}else{
			proxyMojo.getLog().info("404 "+target);
			response.sendError(HttpServletResponse.SC_NOT_FOUND,"No artifact found in proxy");
		}
		baseRequest.setHandled(true);
	}


}
