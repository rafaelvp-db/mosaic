package com.databricks.mosaic.core.geometry

import java.nio.ByteBuffer

import com.esri.core.geometry.SpatialReference
import com.esri.core.geometry.ogc._
import org.locationtech.jts.io.{WKBReader, WKBWriter}

import org.apache.spark.sql.catalyst.InternalRow

import com.databricks.mosaic.core.geometry.linestring.MosaicLineStringOGC
import com.databricks.mosaic.core.geometry.multilinestring.MosaicMultiLineStringOGC
import com.databricks.mosaic.core.geometry.multipoint.MosaicMultiPointOGC
import com.databricks.mosaic.core.geometry.multipolygon.MosaicMultiPolygonOGC
import com.databricks.mosaic.core.geometry.point.{MosaicPoint, MosaicPointOGC}
import com.databricks.mosaic.core.geometry.polygon.MosaicPolygonOGC
import com.databricks.mosaic.core.types.model.{GeometryTypeEnum, InternalGeometry}
import com.databricks.mosaic.core.types.model.GeometryTypeEnum._

abstract class MosaicGeometryOGC(geom: OGCGeometry)
  extends MosaicGeometry {

  override def getAPI: String = "OGC"

  override def getCentroid: MosaicPoint = MosaicPointOGC(geom.centroid())

  override def isEmpty: Boolean = geom.isEmpty

  override def buffer(distance: Double): MosaicGeometry = MosaicGeometryOGC(geom.buffer(distance))

  override def simplify(tolerance: Double): MosaicGeometry = MosaicGeometryOGC(geom.makeSimple())

  override def intersection(other: MosaicGeometry): MosaicGeometry = {
    val otherGeom = other.asInstanceOf[MosaicGeometryOGC].getGeom
    MosaicGeometryOGC(this.getGeom.intersection(otherGeom))
  }

  /**
   * The naming convention in ESRI bindings is different.
   * isSimple actually reflects validity of a geometry.
   *
   * @see [[OGCGeometry]] for isSimple documentation.
   * @return A boolean flag indicating validity.
   */
  override def isValid: Boolean = geom.isSimple

  override def getGeometryType: String = geom.geometryType()

  override def getArea: Double = geom.getEsriGeometry.calculateArea2D()

  override def equals(other: MosaicGeometry): Boolean = {
    val otherGeom = other.asInstanceOf[MosaicGeometryOGC].getGeom
    // required to use object equals to perform exact equals
    //noinspection ComparingUnrelatedTypes
    this.getGeom.equals(otherGeom.asInstanceOf[Object])
  }

  def getGeom: OGCGeometry = geom

  override def equals(other: java.lang.Object): Boolean = false

  override def hashCode: Int = geom.hashCode()

  override def boundary: MosaicGeometry = MosaicGeometryOGC(geom.boundary())

  override def distance(geom2: MosaicGeometry): Double = this.getGeom.distance(geom2.asInstanceOf[MosaicGeometryOGC].getGeom)

  override def toWKB: Array[Byte] = geom.asBinary().array()

  override def toWKT: String = geom.asText()

  override def toJSON: String = geom.asGeoJson()

  override def toHEX: String = WKBWriter.toHex(geom.asBinary().array())

}

object MosaicGeometryOGC extends GeometryReader {

  val spatialReference: SpatialReference = SpatialReference.create(4326)

  override def fromWKT(wkt: String): MosaicGeometry = MosaicGeometryOGC(OGCGeometry.fromText(wkt))

  override def fromHEX(hex: String): MosaicGeometry = {
    val bytes = WKBReader.hexToBytes(hex)
    fromWKB(bytes)
  }

  override def fromWKB(wkb: Array[Byte]): MosaicGeometryOGC = MosaicGeometryOGC(OGCGeometry.fromBinary(ByteBuffer.wrap(wkb)))

  def apply(geom: OGCGeometry): MosaicGeometryOGC = GeometryTypeEnum.fromString(geom.geometryType()) match {
    case POINT => MosaicPointOGC(geom)
    case MULTIPOINT => MosaicMultiPointOGC(geom)
    case POLYGON => MosaicPolygonOGC(geom)
    case MULTIPOLYGON => MosaicMultiPolygonOGC(geom)
    case LINESTRING => MosaicLineStringOGC(geom)
    case MULTILINESTRING => MosaicMultiLineStringOGC(geom)
    // Hotfix for intersections that generate a geometry collection
    // TODO: Decide if intersection is a generator function
    // TODO: Decide if we auto flatten geometry collections
    case GEOMETRYCOLLECTION =>
      val geomCollection = geom.asInstanceOf[OGCGeometryCollection]
      val geometries = for (i <- 0 until geomCollection.numGeometries()) yield geomCollection.geometryN(i)
      geometries.find(g => Seq(POLYGON, MULTIPOLYGON).contains(GeometryTypeEnum.fromString(g.geometryType()))) match {
        case Some(firstChip) if GeometryTypeEnum.fromString(firstChip.geometryType()).id == POLYGON.id      =>
          MosaicPolygonOGC(firstChip)
        case Some(firstChip) if GeometryTypeEnum.fromString(firstChip.geometryType()).id == MULTIPOLYGON.id =>
          MosaicMultiPolygonOGC(firstChip)
        case None => MosaicPolygonOGC.fromWKT("POLYGON EMPTY").asInstanceOf[MosaicGeometryOGC]
      }
  }

  override def fromJSON(geoJson: String): MosaicGeometry = MosaicGeometryOGC(OGCGeometry.fromGeoJson(geoJson))

  override def fromPoints(points: Seq[MosaicPoint], geomType: GeometryTypeEnum.Value): MosaicGeometry = {
    reader(geomType.id).fromPoints(points, geomType)
  }

  def reader(geomTypeId: Int): GeometryReader = GeometryTypeEnum.fromId(geomTypeId) match {
    case POINT => MosaicPointOGC
    case MULTIPOINT => MosaicMultiPointOGC
    case POLYGON => MosaicPolygonOGC
    case MULTIPOLYGON => MosaicMultiPolygonOGC
    case LINESTRING => MosaicLineStringOGC
    case MULTILINESTRING => MosaicMultiLineStringOGC
  }

  override def fromInternal(row: InternalRow): MosaicGeometry = {
    val internalGeometry = InternalGeometry(row)
    reader(internalGeometry.typeId).fromInternal(row)
  }

}
