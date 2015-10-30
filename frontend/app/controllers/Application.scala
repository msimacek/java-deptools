package controllers

import collection.JavaConverters._
import java.io.File
import play.api.mvc.{ Controller, Action }
import org.fedoraproject.javadeptools.Query
import play.api.mvc.Request
import play.api.Play
import com.google.inject.Guice
import org.fedoraproject.javadeptools.impl.JavaDeptoolsModule
import org.fedoraproject.javadeptools.data.ClassEntryDao
import org.fedoraproject.javadeptools.data.FileArtifactDao
import org.fedoraproject.javadeptools.data.PackageDao
import org.fedoraproject.javadeptools.data.PackageCollectionDao
import scala.collection.immutable.HashMap
import com.google.inject.persist.jpa.JpaPersistModule

object Page {
  val itemsPerPage = 100
  def create[T](query: Query[T], currentPage: Int)(implicit request: Request[Any]) = {
    val total = query.getTotal
    val pages = total / itemsPerPage + 1
    if (currentPage < 1 || currentPage > pages) {
      None
    } else {
      Some(new Page(query.getResults((currentPage - 1) * itemsPerPage, itemsPerPage).asScala, currentPage, total))
    }
  }
}

case class Page[T](content: Iterable[T], currentPage: Int, totalCount: Long) {
  val from = (currentPage - 1) * Page.itemsPerPage
  val to = from + content.size
  val maxPage = totalCount / Page.itemsPerPage
}

object Application extends Controller {

  val dbProps = HashMap("javax.persistence.jdbc.url" ->
                          Play.current.configuration.getString("db.default.url").get,
                          "javax.persistence.jdbc.driver" ->
                          Play.current.configuration.getString("db.default.driver").get,
                          "javax.persistence.jdbc.user" ->
                          Play.current.configuration.getString("db.default.user").get,
                          "javax.persistence.jdbc.password" ->
                          Play.current.configuration.getString("db.default.password").get)
  lazy val injector = JavaDeptoolsModule.createInjector(dbProps.asJava)
  lazy val collectionDao = injector.getInstance(classOf[PackageCollectionDao])
  lazy val classDao = injector.getInstance(classOf[ClassEntryDao])
  lazy val fileDao = injector.getInstance(classOf[FileArtifactDao])
  lazy val packageDao = injector.getInstance(classOf[PackageDao])

  def index(page: Int, q: String, collectionName: Option[String]) = Action { implicit request =>
    val collections = collectionDao.getAllCollections.asScala;
    val collection = collectionName.flatMap { name => collections.find(_.getName() == name) }
                                   .getOrElse(collections.head)
    if (q == "") {
      Ok(views.html.index(collections, collection, None))
    } else {
      val query = classDao.queryClassEntriesByName(collection, q + '%')
      val content = Page.create(query, page)
      Ok(views.html.index(collections, collection, content))
    }
  }

  def about = Action(implicit request => Ok(views.html.about()))

  def packageDetail(collectionName: String, name: String) = Action { implicit request =>
    val pkg = for {
      collection <- Option(collectionDao.getCollectionByName(collectionName))
      pkg <- Option(packageDao.getPackageByName(collection, name))
    } yield pkg
    pkg match {
      case None => NotFound
      case Some(pkg) => Ok(views.html.package_detail(pkg))
    }
  }

  def fileArtifactDetail(pkgName: String, fileId: Long) = Action { implicit request =>
    val file = fileDao.findById(fileId);
    Ok(views.html.file_detail(file))
  }
}