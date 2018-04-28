package com.vanniktech.dependency.graph.generator

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.vanniktech.dependency.graph.generator.DependencyGraphGeneratorExtension.Generator.Companion.ALL
import com.vanniktech.dependency.graph.generator.dot.Color
import com.vanniktech.dependency.graph.generator.dot.Color.Companion.MAX_COLOR_VALUE
import com.vanniktech.dependency.graph.generator.dot.GraphFormattingOptions
import com.vanniktech.dependency.graph.generator.dot.Header
import com.vanniktech.dependency.graph.generator.dot.Shape
import com.vanniktech.dependency.graph.generator.dot.Style
import org.assertj.core.api.Java6Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Random

class DotGeneratorTest {
  private lateinit var singleEmpty: Project
  private lateinit var singleProject: Project
  private lateinit var rxjavaProject: Project
  private lateinit var multiProject: Project
  private lateinit var androidProject: DefaultProject // We always need to call evaluate() for Android Projects.
  private lateinit var androidProjectExtension: AppExtension

  @Before @Suppress("Detekt.LongMethod") fun setUp() {
    singleEmpty = ProjectBuilder.builder().withName("singleempty").build()
    singleEmpty.plugins.apply(JavaLibraryPlugin::class.java)
    singleEmpty.repositories.run { add(mavenCentral()) }

    singleProject = ProjectBuilder.builder().withName("single").build()
    singleProject.plugins.apply(JavaLibraryPlugin::class.java)
    singleProject.repositories.run { add(mavenCentral()) }
    singleProject.dependencies.add("api", "org.jetbrains.kotlin:kotlin-stdlib:1.2.30")
    singleProject.dependencies.add("implementation", "io.reactivex.rxjava2:rxjava:2.1.10")

    rxjavaProject = ProjectBuilder.builder().withName("rxjava").build()
    rxjavaProject.plugins.apply(JavaLibraryPlugin::class.java)
    rxjavaProject.repositories.run { add(mavenCentral()) }
    rxjavaProject.dependencies.add("implementation", "io.reactivex.rxjava2:rxjava:2.1.10")

    multiProject = ProjectBuilder.builder().withName("multi").build()

    val multiProject1 = ProjectBuilder.builder().withParent(multiProject).withName("multi1").build()
    multiProject1.plugins.apply(JavaLibraryPlugin::class.java)
    multiProject1.repositories.run { add(mavenCentral()) }
    multiProject1.dependencies.add("api", "org.jetbrains.kotlin:kotlin-stdlib:1.2.30")
    multiProject1.dependencies.add("implementation", "io.reactivex.rxjava2:rxjava:2.1.10")

    val multiProject2 = ProjectBuilder.builder().withParent(multiProject).withName("multi2").build()
    multiProject2.plugins.apply(JavaLibraryPlugin::class.java)
    multiProject2.repositories.run { add(mavenCentral()) }
    multiProject2.dependencies.add("implementation", "io.reactivex.rxjava2:rxjava:2.1.10")
    multiProject2.dependencies.add("implementation", "io.reactivex.rxjava2:rxandroid:2.0.2")

    androidProject = ProjectBuilder.builder().withName("android").build() as DefaultProject
    androidProject.plugins.apply(AppPlugin::class.java)
    androidProject.repositories.run {
      add(mavenCentral())
      add(google())
    }

    androidProjectExtension = androidProject.extensions.getByType(AppExtension::class.java)
    androidProjectExtension.compileSdkVersion(27)
    val manifestFile = File(androidProject.projectDir, "src/main/AndroidManifest.xml")
    manifestFile.parentFile.mkdirs()
    manifestFile.writeText("""
        |<?xml version="1.0" encoding="utf-8"?>
        |<manifest package="com.foo.bar" xmlns:android="http://schemas.android.com/apk/res/android">
        |  <application/>
        |</manifest>""".trimMargin())
  }

  @Test fun singleProjectAllNoTestDependencies() {
    singleEmpty.dependencies.add("testImplementation", "junit:junit:4.12")

    assertThat(DotGenerator(singleEmpty, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  singleempty [label="singleempty", shape="box"];
        |}
        |""".trimMargin())
  }

  @Test fun singleProjectEmptyAllNoProjects() {
    assertThat(DotGenerator(singleEmpty, ALL.copy(includeProject = { false })).generateContent()).isEqualTo("""
        |digraph G {
        |}
        |""".trimMargin())
  }

  @Test fun singleProjectEmptyAll() {
    assertThat(DotGenerator(singleEmpty, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  singleempty [label="singleempty", shape="box"];
        |}
        |""".trimMargin())
  }

  @Test fun singleProjectEmptyAllHeader() {
    assertThat(DotGenerator(singleEmpty, ALL.copy(header = Header("my custom header"))).generateContent()).isEqualTo("""
        |digraph G {
        |  label="my custom header" fontsize="24" height="5" labelloc="t" labeljust="c";
        |  singleempty [label="singleempty", shape="box"];
        |}
        |""".trimMargin())
  }

  @Test fun singleProjectEmptyAllRootFormatted() {
    assertThat(DotGenerator(singleEmpty, ALL.copy(rootFormattingOptions = GraphFormattingOptions(Shape.EGG, Style.DOTTED, Color.fromHex("#ff0099")))).generateContent()).isEqualTo("""
        |digraph G {
        |  singleempty [label="singleempty", shape="egg", style="dotted", color="#ff0099"];
        |}
        |""".trimMargin())
  }

  @Test fun singleProjectAll() {
    assertThat(DotGenerator(singleProject, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  single [label="single", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib [label="kotlin-stdlib", shape="box"];
        |  single -> orgjetbrainskotlinkotlinstdlib;
        |  orgjetbrainsannotations [label="jetbrains-annotations", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib -> orgjetbrainsannotations;
        |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
        |  single -> ioreactivexrxjava2rxjava;
        |  orgreactivestreamsreactivestreams [label="reactive-streams", shape="box"];
        |  ioreactivexrxjava2rxjava -> orgreactivestreamsreactivestreams;
        |}
        |""".trimMargin())
  }

  @Test fun singleProjectAllDependencyFormattingOptions() {
    // Generate a color for each dependency.
    val dependencyFormattingOptions: (ResolvedDependency) -> GraphFormattingOptions = {
      val random = Random(it.hashCode().toLong())
      GraphFormattingOptions(color = Color.fromRgb(random.nextInt(MAX_COLOR_VALUE), random.nextInt(MAX_COLOR_VALUE), random.nextInt(MAX_COLOR_VALUE))
      )
    }

    assertThat(DotGenerator(singleProject, ALL.copy(dependencyFormattingOptions = dependencyFormattingOptions)).generateContent()).isEqualTo("""
        |digraph G {
        |  single [label="single", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib [label="kotlin-stdlib", shape="box", color="#6ba46e"];
        |  single -> orgjetbrainskotlinkotlinstdlib;
        |  orgjetbrainsannotations [label="jetbrains-annotations", shape="box", color="#4a09b2"];
        |  orgjetbrainskotlinkotlinstdlib -> orgjetbrainsannotations;
        |  ioreactivexrxjava2rxjava [label="rxjava", shape="box", color="#cb660b"];
        |  single -> ioreactivexrxjava2rxjava;
        |  orgreactivestreamsreactivestreams [label="reactive-streams", shape="box", color="#7c70b6"];
        |  ioreactivexrxjava2rxjava -> orgreactivestreamsreactivestreams;
        |}
        |""".trimMargin())
  }

  @Test fun singleProjectNoChildren() {
    assertThat(DotGenerator(singleProject, ALL.copy(children = { false })).generateContent()).isEqualTo("""
        |digraph G {
        |  single [label="single", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib [label="kotlin-stdlib", shape="box"];
        |  single -> orgjetbrainskotlinkotlinstdlib;
        |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
        |  single -> ioreactivexrxjava2rxjava;
        |}
        |""".trimMargin())
  }

  @Test fun singleProjectFilterRxJavaOut() {
    assertThat(DotGenerator(singleProject, ALL.copy(include = { it.moduleGroup != "io.reactivex.rxjava2" })).generateContent()).isEqualTo("""
        |digraph G {
        |  single [label="single", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib [label="kotlin-stdlib", shape="box"];
        |  single -> orgjetbrainskotlinkotlinstdlib;
        |  orgjetbrainsannotations [label="jetbrains-annotations", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib -> orgjetbrainsannotations;
        |}
        |""".trimMargin())
  }

  @Test fun recursiveDependencies() {
      singleEmpty.dependencies.add("implementation", "org.apache.xmlgraphics:batik-gvt:1.7")

      assertThat(DotGenerator(singleEmpty, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  singleempty [label="singleempty", shape="box"];
        |  orgapachexmlgraphicsbatikgvt [label="batik-gvt", shape="box"];
        |  singleempty -> orgapachexmlgraphicsbatikgvt;
        |  orgapachexmlgraphicsbatikbridge [label="batik-bridge", shape="box"];
        |  orgapachexmlgraphicsbatikgvt -> orgapachexmlgraphicsbatikbridge;
        |  orgapachexmlgraphicsbatikscript [label="batik-script", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatikscript;
        |  orgapachexmlgraphicsbatikbridge [label="batik-bridge", shape="box"];
        |  orgapachexmlgraphicsbatikscript -> orgapachexmlgraphicsbatikbridge;
        |  orgapachexmlgraphicsbatikgvt [label="batik-gvt", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatikgvt;
        |  orgapachexmlgraphicsbatikawtutil [label="batik-awt-util", shape="box"];
        |  orgapachexmlgraphicsbatikgvt -> orgapachexmlgraphicsbatikawtutil;
        |  orgapachexmlgraphicsbatikutil [label="batik-util", shape="box"];
        |  orgapachexmlgraphicsbatikawtutil -> orgapachexmlgraphicsbatikutil;
        |  orgapachexmlgraphicsbatikutil [label="batik-util", shape="box"];
        |  orgapachexmlgraphicsbatikgvt -> orgapachexmlgraphicsbatikutil;
        |  xmlapisxmlapis [label="xml-apis", shape="box"];
        |  orgapachexmlgraphicsbatikgvt -> xmlapisxmlapis;
        |  orgapachexmlgraphicsbatiksvgdom [label="batik-svg-dom", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatiksvgdom;
        |  orgapachexmlgraphicsbatikanim [label="batik-anim", shape="box"];
        |  orgapachexmlgraphicsbatiksvgdom -> orgapachexmlgraphicsbatikanim;
        |  orgapachexmlgraphicsbatiksvgdom [label="batik-svg-dom", shape="box"];
        |  orgapachexmlgraphicsbatikanim -> orgapachexmlgraphicsbatiksvgdom;
        |  orgapachexmlgraphicsbatikparser [label="batik-parser", shape="box"];
        |  orgapachexmlgraphicsbatiksvgdom -> orgapachexmlgraphicsbatikparser;
        |  orgapachexmlgraphicsbatikawtutil [label="batik-awt-util", shape="box"];
        |  orgapachexmlgraphicsbatikparser -> orgapachexmlgraphicsbatikawtutil;
        |  orgapachexmlgraphicsbatikxml [label="batik-xml", shape="box"];
        |  orgapachexmlgraphicsbatikparser -> orgapachexmlgraphicsbatikxml;
        |  orgapachexmlgraphicsbatikutil [label="batik-util", shape="box"];
        |  orgapachexmlgraphicsbatikxml -> orgapachexmlgraphicsbatikutil;
        |  orgapachexmlgraphicsbatikutil [label="batik-util", shape="box"];
        |  orgapachexmlgraphicsbatikparser -> orgapachexmlgraphicsbatikutil;
        |  orgapachexmlgraphicsbatikawtutil [label="batik-awt-util", shape="box"];
        |  orgapachexmlgraphicsbatiksvgdom -> orgapachexmlgraphicsbatikawtutil;
        |  orgapachexmlgraphicsbatikdom [label="batik-dom", shape="box"];
        |  orgapachexmlgraphicsbatiksvgdom -> orgapachexmlgraphicsbatikdom;
        |  orgapachexmlgraphicsbatikcss [label="batik-css", shape="box"];
        |  orgapachexmlgraphicsbatikdom -> orgapachexmlgraphicsbatikcss;
        |  orgapachexmlgraphicsbatikutil [label="batik-util", shape="box"];
        |  orgapachexmlgraphicsbatikcss -> orgapachexmlgraphicsbatikutil;
        |  orgapachexmlgraphicsbatikext [label="batik-ext", shape="box"];
        |  orgapachexmlgraphicsbatikcss -> orgapachexmlgraphicsbatikext;
        |  xmlapisxmlapis [label="xml-apis", shape="box"];
        |  orgapachexmlgraphicsbatikext -> xmlapisxmlapis;
        |  xmlapisxmlapis [label="xml-apis", shape="box"];
        |  orgapachexmlgraphicsbatikcss -> xmlapisxmlapis;
        |  xmlapisxmlapisext [label="xml-apis-ext", shape="box"];
        |  orgapachexmlgraphicsbatikcss -> xmlapisxmlapisext;
        |  orgapachexmlgraphicsbatikxml [label="batik-xml", shape="box"];
        |  orgapachexmlgraphicsbatikdom -> orgapachexmlgraphicsbatikxml;
        |  orgapachexmlgraphicsbatikutil [label="batik-util", shape="box"];
        |  orgapachexmlgraphicsbatikdom -> orgapachexmlgraphicsbatikutil;
        |  orgapachexmlgraphicsbatikext [label="batik-ext", shape="box"];
        |  orgapachexmlgraphicsbatikdom -> orgapachexmlgraphicsbatikext;
        |  xalanxalan [label="xalan", shape="box"];
        |  orgapachexmlgraphicsbatikdom -> xalanxalan;
        |  xmlapisxmlapis [label="xml-apis", shape="box"];
        |  xalanxalan -> xmlapisxmlapis;
        |  xmlapisxmlapis [label="xml-apis", shape="box"];
        |  orgapachexmlgraphicsbatikdom -> xmlapisxmlapis;
        |  xmlapisxmlapisext [label="xml-apis-ext", shape="box"];
        |  orgapachexmlgraphicsbatikdom -> xmlapisxmlapisext;
        |  orgapachexmlgraphicsbatikcss [label="batik-css", shape="box"];
        |  orgapachexmlgraphicsbatiksvgdom -> orgapachexmlgraphicsbatikcss;
        |  orgapachexmlgraphicsbatikutil [label="batik-util", shape="box"];
        |  orgapachexmlgraphicsbatiksvgdom -> orgapachexmlgraphicsbatikutil;
        |  orgapachexmlgraphicsbatikext [label="batik-ext", shape="box"];
        |  orgapachexmlgraphicsbatiksvgdom -> orgapachexmlgraphicsbatikext;
        |  xmlapisxmlapis [label="xml-apis", shape="box"];
        |  orgapachexmlgraphicsbatiksvgdom -> xmlapisxmlapis;
        |  xmlapisxmlapisext [label="xml-apis-ext", shape="box"];
        |  orgapachexmlgraphicsbatiksvgdom -> xmlapisxmlapisext;
        |  orgapachexmlgraphicsbatikparser [label="batik-parser", shape="box"];
        |  orgapachexmlgraphicsbatikanim -> orgapachexmlgraphicsbatikparser;
        |  orgapachexmlgraphicsbatikawtutil [label="batik-awt-util", shape="box"];
        |  orgapachexmlgraphicsbatikanim -> orgapachexmlgraphicsbatikawtutil;
        |  orgapachexmlgraphicsbatikdom [label="batik-dom", shape="box"];
        |  orgapachexmlgraphicsbatikanim -> orgapachexmlgraphicsbatikdom;
        |  orgapachexmlgraphicsbatikutil [label="batik-util", shape="box"];
        |  orgapachexmlgraphicsbatikanim -> orgapachexmlgraphicsbatikutil;
        |  orgapachexmlgraphicsbatikext [label="batik-ext", shape="box"];
        |  orgapachexmlgraphicsbatikanim -> orgapachexmlgraphicsbatikext;
        |  xmlapisxmlapis [label="xml-apis", shape="box"];
        |  orgapachexmlgraphicsbatikanim -> xmlapisxmlapis;
        |  xmlapisxmlapisext [label="xml-apis-ext", shape="box"];
        |  orgapachexmlgraphicsbatikanim -> xmlapisxmlapisext;
        |  orgapachexmlgraphicsbatikanim [label="batik-anim", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatikanim;
        |  orgapachexmlgraphicsbatikparser [label="batik-parser", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatikparser;
        |  orgapachexmlgraphicsbatikawtutil [label="batik-awt-util", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatikawtutil;
        |  orgapachexmlgraphicsbatikdom [label="batik-dom", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatikdom;
        |  orgapachexmlgraphicsbatikcss [label="batik-css", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatikcss;
        |  orgapachexmlgraphicsbatikxml [label="batik-xml", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatikxml;
        |  orgapachexmlgraphicsbatikutil [label="batik-util", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatikutil;
        |  orgapachexmlgraphicsbatikext [label="batik-ext", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> orgapachexmlgraphicsbatikext;
        |  xalanxalan [label="xalan", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> xalanxalan;
        |  xmlapisxmlapis [label="xml-apis", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> xmlapisxmlapis;
        |  xmlapisxmlapisext [label="xml-apis-ext", shape="box"];
        |  orgapachexmlgraphicsbatikbridge -> xmlapisxmlapisext;
        |  orgapachexmlgraphicsbatiksvgdom [label="batik-svg-dom", shape="box"];
        |  orgapachexmlgraphicsbatikscript -> orgapachexmlgraphicsbatiksvgdom;
        |  orgapachexmlgraphicsbatikdom [label="batik-dom", shape="box"];
        |  orgapachexmlgraphicsbatikscript -> orgapachexmlgraphicsbatikdom;
        |  orgapachexmlgraphicsbatikutil [label="batik-util", shape="box"];
        |  orgapachexmlgraphicsbatikscript -> orgapachexmlgraphicsbatikutil;
        |  orgapachexmlgraphicsbatikext [label="batik-ext", shape="box"];
        |  orgapachexmlgraphicsbatikscript -> orgapachexmlgraphicsbatikext;
        |  orgapachexmlgraphicsbatikjs [label="batik-js", shape="box"];
        |  orgapachexmlgraphicsbatikscript -> orgapachexmlgraphicsbatikjs;
        |  xmlapisxmlapis [label="xml-apis", shape="box"];
        |  orgapachexmlgraphicsbatikjs -> xmlapisxmlapis;
        |  xmlapisxmlapis [label="xml-apis", shape="box"];
        |  orgapachexmlgraphicsbatikscript -> xmlapisxmlapis;
        |}
        |""".trimMargin())
  }

  @Test fun singleProjectNoDuplicateDependencyConnections() {
    // Both RxJava and RxAndroid point transitively on reactivestreams.
    singleProject.dependencies.add("implementation", "io.reactivex.rxjava2:rxandroid:2.0.2")

    assertThat(DotGenerator(singleProject, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  single [label="single", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib [label="kotlin-stdlib", shape="box"];
        |  single -> orgjetbrainskotlinkotlinstdlib;
        |  orgjetbrainsannotations [label="jetbrains-annotations", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib -> orgjetbrainsannotations;
        |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
        |  single -> ioreactivexrxjava2rxjava;
        |  orgreactivestreamsreactivestreams [label="reactive-streams", shape="box"];
        |  ioreactivexrxjava2rxjava -> orgreactivestreamsreactivestreams;
        |  ioreactivexrxjava2rxandroid [label="rxandroid", shape="box"];
        |  single -> ioreactivexrxjava2rxandroid;
        |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
        |  ioreactivexrxjava2rxandroid -> ioreactivexrxjava2rxjava;
        |}
        |""".trimMargin())
  }

  @Test fun multiProjectAll() {
    assertThat(DotGenerator(multiProject, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  multimulti1 [label="multi1", shape="box"];
        |  multimulti2 [label="multi2", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib [label="kotlin-stdlib", shape="box"];
        |  multimulti1 -> orgjetbrainskotlinkotlinstdlib;
        |  orgjetbrainsannotations [label="jetbrains-annotations", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib -> orgjetbrainsannotations;
        |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
        |  multimulti1 -> ioreactivexrxjava2rxjava;
        |  orgreactivestreamsreactivestreams [label="reactive-streams", shape="box"];
        |  ioreactivexrxjava2rxjava -> orgreactivestreamsreactivestreams;
        |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
        |  multimulti2 -> ioreactivexrxjava2rxjava;
        |  ioreactivexrxjava2rxandroid [label="rxandroid", shape="box"];
        |  multimulti2 -> ioreactivexrxjava2rxandroid;
        |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
        |  ioreactivexrxjava2rxandroid -> ioreactivexrxjava2rxjava;
        |}
        |""".trimMargin())
  }

  @Test fun androidProjectArchitectureComponents() {
    androidProject.evaluate()

    androidProject.dependencies.add("implementation", "android.arch.persistence.room:runtime:1.0.0")

    assertThat(DotGenerator(androidProject, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  android [label="android", shape="box"];
        |  androidarchpersistenceroomruntime [label="persistence-room-runtime", shape="box"];
        |  android -> androidarchpersistenceroomruntime;
        |  androidarchpersistenceroomcommon [label="persistence-room-common", shape="box"];
        |  androidarchpersistenceroomruntime -> androidarchpersistenceroomcommon;
        |  comandroidsupportsupportannotations [label="support-annotations", shape="box"];
        |  androidarchpersistenceroomcommon -> comandroidsupportsupportannotations;
        |  androidarchpersistencedbframework [label="persistence-db-framework", shape="box"];
        |  androidarchpersistenceroomruntime -> androidarchpersistencedbframework;
        |  androidarchpersistencedb [label="persistence-db", shape="box"];
        |  androidarchpersistencedbframework -> androidarchpersistencedb;
        |  comandroidsupportsupportannotations [label="support-annotations", shape="box"];
        |  androidarchpersistencedb -> comandroidsupportsupportannotations;
        |  comandroidsupportsupportannotations [label="support-annotations", shape="box"];
        |  androidarchpersistencedbframework -> comandroidsupportsupportannotations;
        |  androidarchpersistencedb [label="persistence-db", shape="box"];
        |  androidarchpersistenceroomruntime -> androidarchpersistencedb;
        |  androidarchcoreruntime [label="core-runtime", shape="box"];
        |  androidarchpersistenceroomruntime -> androidarchcoreruntime;
        |  androidarchcorecommon [label="core-common", shape="box"];
        |  androidarchcoreruntime -> androidarchcorecommon;
        |  comandroidsupportsupportannotations [label="support-annotations", shape="box"];
        |  androidarchcorecommon -> comandroidsupportsupportannotations;
        |  comandroidsupportsupportannotations [label="support-annotations", shape="box"];
        |  androidarchcoreruntime -> comandroidsupportsupportannotations;
        |  comandroidsupportsupportcoreutils [label="support-core-utils", shape="box"];
        |  androidarchpersistenceroomruntime -> comandroidsupportsupportcoreutils;
        |  comandroidsupportsupportcompat [label="support-compat", shape="box"];
        |  comandroidsupportsupportcoreutils -> comandroidsupportsupportcompat;
        |  comandroidsupportsupportannotations [label="support-annotations", shape="box"];
        |  comandroidsupportsupportcompat -> comandroidsupportsupportannotations;
        |  comandroidsupportsupportannotations [label="support-annotations", shape="box"];
        |  comandroidsupportsupportcoreutils -> comandroidsupportsupportannotations;
        |}
        |""".trimMargin())
  }

  @Test fun androidProjectSqlDelight() {
    androidProject.evaluate()

    androidProject.dependencies.add("implementation", "com.squareup.sqldelight:runtime:0.6.1")

    assertThat(DotGenerator(androidProject, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  android [label="android", shape="box"];
        |  comsquareupsqldelightruntime [label="sqldelight-runtime", shape="box"];
        |  android -> comsquareupsqldelightruntime;
        |  comandroidsupportsupportannotations [label="support-annotations", shape="box"];
        |  comsquareupsqldelightruntime -> comandroidsupportsupportannotations;
        |}
        |""".trimMargin())
  }

  @Test fun androidProjectIncludeAllFlavorsByDefault() {
    androidProjectExtension.flavorDimensions("test")
    androidProjectExtension.productFlavors {
      it.create("flavor1").dimension = "test"
      it.create("flavor2").dimension = "test"
    }

    androidProject.evaluate()

    androidProject.dependencies.add("flavor1Implementation", "io.reactivex.rxjava2:rxandroid:2.0.2")
    androidProject.dependencies.add("flavor2DebugImplementation", "io.reactivex.rxjava2:rxjava:2.1.10")
    androidProject.dependencies.add("flavor2ReleaseImplementation", "org.jetbrains.kotlin:kotlin-stdlib:1.2.30")

    assertThat(DotGenerator(androidProject, ALL).generateContent()).isEqualTo("""
      |digraph G {
      |  android [label="android", shape="box"];
      |  ioreactivexrxjava2rxandroid [label="rxandroid", shape="box"];
      |  android -> ioreactivexrxjava2rxandroid;
      |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
      |  ioreactivexrxjava2rxandroid -> ioreactivexrxjava2rxjava;
      |  orgreactivestreamsreactivestreams [label="reactive-streams", shape="box"];
      |  ioreactivexrxjava2rxjava -> orgreactivestreamsreactivestreams;
      |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
      |  android -> ioreactivexrxjava2rxjava;
      |  orgjetbrainskotlinkotlinstdlib [label="kotlin-stdlib", shape="box"];
      |  android -> orgjetbrainskotlinkotlinstdlib;
      |  orgjetbrainsannotations [label="jetbrains-annotations", shape="box"];
      |  orgjetbrainskotlinkotlinstdlib -> orgjetbrainsannotations;
      |}
      |""".trimMargin())
  }

  @Test fun androidProjectIncludeAllBuildTypesByDefault() {
    androidProjectExtension.buildTypes {
      it.create("staging")
    }

    androidProject.evaluate()

    androidProject.dependencies.add("releaseImplementation", "io.reactivex.rxjava2:rxandroid:2.0.2")
    androidProject.dependencies.add("debugImplementation", "io.reactivex.rxjava2:rxjava:2.1.10")
    androidProject.dependencies.add("stagingImplementation", "org.jetbrains.kotlin:kotlin-stdlib:1.2.30")

    assertThat(DotGenerator(androidProject, ALL).generateContent()).isEqualTo("""
      |digraph G {
      |  android [label="android", shape="box"];
      |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
      |  android -> ioreactivexrxjava2rxjava;
      |  orgreactivestreamsreactivestreams [label="reactive-streams", shape="box"];
      |  ioreactivexrxjava2rxjava -> orgreactivestreamsreactivestreams;
      |  ioreactivexrxjava2rxandroid [label="rxandroid", shape="box"];
      |  android -> ioreactivexrxjava2rxandroid;
      |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
      |  ioreactivexrxjava2rxandroid -> ioreactivexrxjava2rxjava;
      |  orgjetbrainskotlinkotlinstdlib [label="kotlin-stdlib", shape="box"];
      |  android -> orgjetbrainskotlinkotlinstdlib;
      |  orgjetbrainsannotations [label="jetbrains-annotations", shape="box"];
      |  orgjetbrainskotlinkotlinstdlib -> orgjetbrainsannotations;
      |}
      |""".trimMargin())
  }

  @Test fun androidProjectIncludeOnlyStagingCompileClasspath() {
    androidProjectExtension.buildTypes {
      it.create("staging")
    }

    androidProject.evaluate()

    androidProject.dependencies.add("releaseImplementation", "io.reactivex.rxjava2:rxandroid:2.0.2")
    androidProject.dependencies.add("debugImplementation", "io.reactivex.rxjava2:rxjava:2.1.10")
    androidProject.dependencies.add("stagingImplementation", "org.jetbrains.kotlin:kotlin-stdlib:1.2.30")

    assertThat(DotGenerator(androidProject, ALL.copy(includeConfiguration = { it.name == "stagingCompileClasspath" })).generateContent()).isEqualTo("""
        |digraph G {
        |  android [label="android", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib [label="kotlin-stdlib", shape="box"];
        |  android -> orgjetbrainskotlinkotlinstdlib;
        |  orgjetbrainsannotations [label="jetbrains-annotations", shape="box"];
        |  orgjetbrainskotlinkotlinstdlib -> orgjetbrainsannotations;
        |}
        |""".trimMargin())
  }

  @Test fun androidProjectDoNotIncludeTestDependency() {
    androidProject.evaluate()

    androidProject.dependencies.add("testImplementation", "junit:junit:4.12")

    assertThat(DotGenerator(androidProject, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  android [label="android", shape="box"];
        |}
        |""".trimMargin())
  }

  @Test fun androidProjectDoNotIncludeAndroidTestDependency() {
    androidProject.evaluate()

    androidProject.dependencies.add("androidTestImplementation", "junit:junit:4.12")

    assertThat(DotGenerator(androidProject, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  android [label="android", shape="box"];
        |}
        |""".trimMargin())
  }

  @Test fun projectNamedLikeDependencyName() {
    assertThat(DotGenerator(rxjavaProject, ALL).generateContent()).isEqualTo("""
        |digraph G {
        |  rxjava [label="rxjava", shape="box"];
        |  ioreactivexrxjava2rxjava [label="rxjava", shape="box"];
        |  rxjava -> ioreactivexrxjava2rxjava;
        |  orgreactivestreamsreactivestreams [label="reactive-streams", shape="box"];
        |  ioreactivexrxjava2rxjava -> orgreactivestreamsreactivestreams;
        |}
        |""".trimMargin())
  }
}
