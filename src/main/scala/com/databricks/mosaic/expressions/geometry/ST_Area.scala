package com.databricks.mosaic.expressions.geometry

import com.databricks.mosaic.core.geometry.api.GeometryAPI
import org.apache.spark.sql.catalyst.expressions.codegen.CodegenFallback
import org.apache.spark.sql.catalyst.expressions.{Expression, ExpressionDescription, NullIntolerant, UnaryExpression}
import org.apache.spark.sql.types.{DataType, DoubleType}

@ExpressionDescription(
  usage =
    "_FUNC_(expr1) - Returns the area of the geometry.",
  examples =
    """
    Examples:
      > SELECT _FUNC_(a);
       15.2512
  """,
  since = "1.0"
)
case class ST_Area(inputGeom: Expression, geometryAPIName: String)
  extends UnaryExpression
    with NullIntolerant
    with CodegenFallback {

  /** ST_Area expression returns are covered by the [[org.locationtech.jts.geom.Geometry]]
   * instance extracted from inputGeom expression.
   */

  override def child: Expression = inputGeom

  /** Output Data Type */
  override def dataType: DataType = DoubleType

  override def nullSafeEval(input1: Any): Any = {
    val geometryAPI = GeometryAPI(geometryAPIName)
    val geom = geometryAPI.geometry(input1, inputGeom.dataType)
    geom.getArea
  }

  override def makeCopy(newArgs: Array[AnyRef]): Expression = {
    val asArray = newArgs.take(1).map(_.asInstanceOf[Expression])
    val res = ST_Area(asArray(0), geometryAPIName)
    res.copyTagsFrom(this)
    res
  }

}
