

![](./logo.png)



### Spring 5.2.10 源码编译分析

- 下载spring的源码

  ```bash
  git clone https://github.com/spring-projects/spring-framework.git
  ```

- 切换到 tag = v5.2.13.RELEASE，然后新建一个分支

  ```bash
  git checkout v5.2.13.RELEASE 
  
- 在源码根目录打开命令行窗口，执行下面命令，然后等待`BUILD SUCCESS`字样的提示，能看到说明编译成功

  ```bash
  gradlew :spring-oxm:compileTestJava
  ```

- 用idea导入spring-framework工程（注意：IDEA版本为2019.3）

**可能出现的问题**

在导入到 IDEA 中，会自动进行 gradle build，但是由于 IDEA gradle 插件的版本原因，可能会出现如下问题。如果 IDEA 不是2019.03版本，根据实际版本选择适当的 gradle 版本即可

```java
exception during working with external system: java.lang.AssertionError
	at org.jetbrains.plugins.gradle.service.project.BaseGradleProjectResolverExtension.createModule(BaseGradleProjectResolverExtension.java:154)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.kotlin.idea.configuration.KotlinMPPGradleProjectResolver.createModule(KotlinMPPGradleProjectResolver.kt:67)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at com.android.tools.idea.gradle.project.sync.idea.AndroidGradleProjectResolver.createModule(AndroidGradleProjectResolver.java:165)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.kotlin.android.configure.KotlinAndroidMPPGradleProjectResolver.createModule(KotlinAndroidMPPGradleProjectResolver.kt:45)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension.createModule(AbstractProjectResolverExtension.java:86)
	at org.jetbrains.plugins.gradle.service.project.TracedProjectResolverExtension.createModule(TracedProjectResolverExtension.java:45)
	at org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.doResolveProjectInfo(GradleProjectResolver.java:344)
	at org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.access$200(GradleProjectResolver.java:60)
	at org.jetbrains.plugins.gradle.service.project.GradleProjectResolver$ProjectConnectionDataNodeFunction.fun(GradleProjectResolver.java:725)
	at org.jetbrains.plugins.gradle.service.project.GradleProjectResolver$ProjectConnectionDataNodeFunction.fun(GradleProjectResolver.java:708)
	at org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper.execute(GradleExecutionHelper.java:278)
	at org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.resolveProjectInfo(GradleProjectResolver.java:125)
	at org.jetbrains.plugins.gradle.service.project.GradleProjectResolver.resolveProjectInfo(GradleProjectResolver.java:60)
	at com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProjectResolverImpl.lambda$resolveProjectInfo$0(RemoteExternalSystemProjectResolverImpl.java:35)
	at com.intellij.openapi.externalSystem.service.remote.AbstractRemoteExternalSystemService.execute(AbstractRemoteExternalSystemService.java:57)
	at com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProjectResolverImpl.resolveProjectInfo(RemoteExternalSystemProjectResolverImpl.java:35)
	at com.intellij.openapi.externalSystem.service.remote.wrapper.ExternalSystemProjectResolverWrapper.resolveProjectInfo(ExternalSystemProjectResolverWrapper.java:44)
	at com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask.doExecute(ExternalSystemResolveProjectTask.java:100)
	at com.intellij.openapi.externalSystem.service.internal.AbstractExternalSystemTask.execute(AbstractExternalSystemTask.java:146)
	at com.intellij.openapi.externalSystem.service.internal.AbstractExternalSystemTask.execute(AbstractExternalSystemTask.java:132)
	at com.intellij.openapi.externalSystem.util.ExternalSystemUtil$3.executeImpl(ExternalSystemUtil.java:540)
	at com.intellij.openapi.externalSystem.util.ExternalSystemUtil$3.lambda$execute$1(ExternalSystemUtil.java:392)
	at com.intellij.openapi.project.DumbServiceImpl.suspendIndexingAndRun(DumbServiceImpl.java:150)
	at com.intellij.openapi.externalSystem.util.ExternalSystemUtil$3.execute(ExternalSystemUtil.java:392)
	at com.intellij.openapi.externalSystem.util.ExternalSystemUtil$5.run(ExternalSystemUtil.java:647)
	at com.intellij.openapi.progress.impl.CoreProgressManager$TaskRunnable.run(CoreProgressManager.java:888)
	at com.intellij.openapi.progress.impl.CoreProgressManager.lambda$runProcess$2(CoreProgressManager.java:163)
	at com.intellij.openapi.progress.impl.CoreProgressManager.registerIndicatorAndRun(CoreProgressManager.java:585)
	at com.intellij.openapi.progress.impl.CoreProgressManager.executeProcessUnderProgress(CoreProgressManager.java:531)
	at com.intellij.openapi.progress.impl.ProgressManagerImpl.executeProcessUnderProgress(ProgressManagerImpl.java:59)
	at com.intellij.openapi.progress.impl.CoreProgressManager.runProcess(CoreProgressManager.java:150)
	at com.intellij.openapi.progress.impl.CoreProgressManager$4.lambda$run$0(CoreProgressManager.java:402)
	at com.intellij.util.ConcurrencyUtil.runUnderThreadName(ConcurrencyUtil.java:221)
	at com.intellij.openapi.progress.impl.CoreProgressManager$4.run(CoreProgressManager.java:402)
	at com.intellij.openapi.application.impl.ApplicationImpl$1.run(ApplicationImpl.java:238)
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:515)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1128)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:628)
	at java.base/java.lang.Thread.run(Thread.java:834)

```

一般是 gradle 版本问题，修改下`/spring-framework/gradle/wrapper/gradle-wrapper.properties`：

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
## 替换成5.x版本
distributionUrl=https\://services.gradle.org/distributions/gradle-5.5.1-all.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists

```
