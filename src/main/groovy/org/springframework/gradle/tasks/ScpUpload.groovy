package org.springframework.gradle.tasks

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Compression;

/**
 * Extends the Tar task, uploading the created archive to a remote directory, unpacking and deleting it.
 * Requires Ant ssh (jsch) support.
 */
class ScpUpload extends Tar {
    @Input
    String remoteDir
    Login login
    @Input
    String host

    def scripts = [ "cd $remoteDir && tar -xjf $archiveName", "rm $remoteDir/$archiveName" ]

    ScpUpload() {
        compression = Compression.BZIP2
        if (project.configurations.findByName('antjsch') == null) {
            project.configurations.add('antjsch')
            project.dependencies {
                antjsch 'org.apache.ant:ant-jsch:1.8.1'
            }
            def classpath = project.configurations.antjsch.asPath
            project.ant {
                taskdef(name: 'scp', classname: 'org.apache.tools.ant.taskdefs.optional.ssh.Scp', classpath: classpath)
                taskdef(name: 'sshexec', classname: 'org.apache.tools.ant.taskdefs.optional.ssh.SSHExec', classpath: classpath)
            }
        }
    }

    @TaskAction
    void copy() {
        super.copy();
        upload();
    }

    def upload() {
        String username = login.username
        String password = login.password
        String key = login.key
        String host = login.host
        
        project.ant {
            if (key != null) {
               scp(file: archivePath, todir: "$username@$host:$remoteDir", keyfile: key)
               
               for (s in scripts) {
                  sshexec(host: host, username: username, keyfile: key, command: s)
               }
            }
            
            else {
               scp(file: archivePath, todir: "$username@$host:$remoteDir", password: password)
               
               for (s in scripts) {
                  sshexec(host: host, username: username, password: password, command: s)
               }
            }
        }
    }

    void setLogin(Login login) {
        dependsOn(login)
        this.login = login
        this.host = login.host
    }
}

/**
 * Stores login information for a remote host.
 */
class Login extends DefaultTask {
    @Input
    String host
    @Input
    @Optional
    String key
    
    @Input
    String username

    @Input
    @Optional
    String password

    @TaskAction
    login() {
        if (key == null || username == null) {
          def console = System.console()
          if (console) {
              if (username == null) {
                username = console.readLine("\nPlease enter the ssh username for host '$host': ")
                
              }
              if (key == null) {
                password = new String(console.readPassword("Please enter the ssh password for '$host': "))
              }
          } else {
              logger.error "Unable to access System.console()."
          }
        }
    }
}