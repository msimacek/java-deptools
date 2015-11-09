package controllers

import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter

import org.fedoraproject.javadeptools.Query
import org.fedoraproject.javadeptools.data.ClassEntryDao
import org.fedoraproject.javadeptools.data.FileArtifactDao
import org.fedoraproject.javadeptools.data.ManifestEntryDao
import org.fedoraproject.javadeptools.data.PackageCollectionDao
import org.fedoraproject.javadeptools.data.PackageDao
import org.fedoraproject.javadeptools.impl.JavaDeptoolsModule
import org.fedoraproject.javadeptools.model.ClassEntry
import org.fedoraproject.javadeptools.model.ManifestEntry

import play.api.Play.current
import play.api.Play
import play.api.data.Form
import play.api.data.Forms.boolean
import play.api.data.Forms.default
import play.api.data.Forms.mapping
import play.api.data.Forms.text
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.Controller
import play.api.mvc.Request
import views.html.helper.FieldConstructor

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

object implicits {
  implicit val fc = FieldConstructor(views.html.field_constructor.f)
}

trait PageTrait {
  def currentPage: Int
  def totalCount: Long
  def from: Int
  def to: Int
  def maxPage: Long
}

case class Page[T](content: Iterable[T], currentPage: Int, totalCount: Long) extends PageTrait {
  val from = (currentPage - 1) * Page.itemsPerPage
  val to = from + content.size
  val maxPage = totalCount / Page.itemsPerPage
}

abstract class SearchResults
object SearchResults {
  def create[T](query: Query[T], pageNo: Int, ctor: Page[T] => SearchResults)(implicit formData: SearchData, request: Request[AnyContent]) = {
    Page.create(query.caseSensitive(formData.caseSensitive), pageNo).map(ctor)
  }
}
case class ClassResults(result: Page[ClassEntry]) extends SearchResults
case class ManifestResults(result: Page[ManifestEntry]) extends SearchResults

case class SearchData(queryType: String, query: String, query2: String,
  collectionName: String, caseSensitive: Boolean)

object Application extends Controller {

  val dbProps = Map("javax.persistence.jdbc.url" ->
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
  lazy val manifestDao = injector.getInstance(classOf[ManifestEntryDao])

  val searchForm = Form(
    mapping(
      "qtype" -> default(text, "classes"),
      "q" -> default(text, ""),
      "q2" -> default(text, ""),
      "collection" -> default(text, ""),
      "cs" -> default(boolean, false))(SearchData.apply)(SearchData.unapply))

  def index(page: Int) = Action { implicit request =>
    val form = searchForm.bindFromRequest
    implicit val formData = form.get
    val collections = collectionDao.getAllCollections.asScala;
    val collection = collections.find(_.getName() == formData.collectionName).getOrElse(collections.head)
    val content = if (formData.query.length > 0) {
      formData.queryType match {
        case "classes" =>
          val query = classDao.queryClassEntriesByName(collection, formData.query + '%')
          SearchResults.create(query, page, ClassResults)
        case "manifests" =>
          val query = manifestDao.queryByManifest(collection, formData.query, '%' + formData.query2 + '%')
          SearchResults.create(query, page, ManifestResults)
        case _ => None
      }
    } else None
    Ok(views.html.index(form, collections, collection, content))
  }

  def about = Action(implicit request => Ok(views.html.about()))

  def packageDetail(collectionName: String, name: String) = Action { implicit request =>
    val pkg = for {
      collection <- Option(collectionDao.getCollectionByName(collectionName))
      pkg <- Option(packageDao.queryPackagesByName(collection, name).getSingleResult)
    } yield pkg
    pkg match {
      case None => NotFound
      case Some(pkg) => Ok(views.html.package_detail(pkg))
    }
  }

  def fileArtifactDetail(fileId: Long) = Action { implicit request =>
    val file = fileDao.findById(fileId);
    Ok(views.html.file_detail(file))
  }
}