package com.google.code.plsqlmaven.plsql

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.project.MavenProject
import org.apache.maven.model.Model
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import com.google.code.plsqlmaven.shared.DDLWriterThread
import org.apache.maven.plugin.logging.Log

/**
 * Compare two different project trees
 * and produce the DDL to drop objects
 * found in the current project and not 
 * in the -Dto project
 * 
 * @goal drop-removed
 */
public class PlSqlDropRemovedMojo
    extends PlSqlMojo
{

   /**
    * Maven project (directory) to compare with the current project
    * @since 1.9
    * @required
    * @parameter expression="${to}"
    */
   protected String to;
   
   /**
    * DDL file writer
    * @component
    */
   private DDLWriterThread ddlWriter;
   
   private static MavenProject compareProject;
   
   public static changes= [];
   
   void execute()
   {
       if (!compareProject) 
       {
            compareProject= dirToProject(to);
            log.debug('ddlwt: '+ddlWriter);
            ddlWriter.getInstance(path2(to,'target'),'drop.sql').registerMojo(this);
       }
       
       log.info project.basedir.absolutePath

       def files= getPlsqlSourceFiles()

       for (file in files)
       {
          def target= getSourceDescriptor(file);
          def source= findSource(target,compareProject);
          generateDDL(source,target)
       }

       log.info 'changes: '+changes.size()
   }
   
   private findSource(source,project)
   {
       def filePath= path2(project.basedir.absolutePath,'src','main','plsql',
                           source.baseType,
                           (source.baseType in ['type','package'] ? source.name.toLowerCase() : ''),
                           source.name.toLowerCase()+'.'+plsqlUtils.getTypeExt(source.type)+'.plsql');
       def file= new File(filePath);
       def exists= file.exists();
       log.debug filePath+' exists? '+(exists ? 'yes' : 'no')
       
       if (exists)
         return plsqlUtils.getSourceDescriptor(file);
       else
       {
           for (module in project.modules)
           {
               log.debug module
               def compareToSource= findSource(source,dirToProject(project.basedir.absolutePath+File.separator+module));
               
               if (compareToSource)
                 return compareToSource;
           }
           
           return null;
       }
   }
   
   private dirToProject(dir)
   {
       Model model = null
       FileReader reader = null
       MavenXpp3Reader mavenreader = new MavenXpp3Reader()
       def pomfile= new File(dir,'pom.xml')
       
       if (!pomfile.exists())
          fail('Compare project not found: '+pomfile.absolutePath);

       reader = new FileReader(pomfile)
       model = mavenreader.read(reader)
       def project= new MavenProject(model)
       project.basedir= new File(dir)
       return project
   }
   
   private generateDDL(source, target)
   {
       log.debug "target: ${target.file.absolutePath}"
              
       if (!source)
           changes << drop(target)
   }
   
   private drop(object)
   {
      return [ ddl: "drop ${object.type} ${object.name}"+(object.type=='TYPE' ? ' force' : ''), 
               privMessage: "you need to grant drop ${object.type} to ${username}" ]
   }

   public Log getLog()
   {
      return super.log
   }

   public reorder(changes)
   {
         return changes
   }

}
