package com.org.propertyutil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
public class GitCloneBranch {
	public String GitCloneRepo(String Gituri, String username, String password, String branch, String reviewCode, String mulehome)
			throws IOException, InvalidRemoteException, TransportException, GitAPIException {
		String repoUrl = Gituri;
		String status = null;
		File tempDir = new File(mulehome);
		tempDir.mkdirs();
		System.out.println("Clone Dir: " + tempDir.toString());
		try {
			System.out.println("Cloning " + repoUrl + " into " + repoUrl);
			Git.cloneRepository().setURI(repoUrl).setBranch(branch).setDirectory(tempDir)
					.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password)).call();
			System.out.println("Completed Cloning");
			status = "Repository cloning is done";
			if ((tempDir.listFiles().length) < 2) {
				throw new Exception("Invalid Branch");
			}
		} catch (GitAPIException e) {
			System.out.println("Exception occured while cloning repo");
			e.printStackTrace();
			status=e.toString();
		} catch (Exception e) {
			e.printStackTrace();
			status=e.toString();
		}
		return status + "," + tempDir + "\\";
	}
}
