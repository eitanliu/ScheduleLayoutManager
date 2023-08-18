package com.eitanliu.schedule.layoutmanager

import org.joda.time.Period

interface IScheduleItem {

    //开始时间
    var startPeriod: Period

    //结束时间
    var endPeriod: Period

    var columnStart: Int
    var columnEnd: Int
    var columnSize: Int

    val columnMeasureWidth get() = (columnEnd - columnStart + 1).toFloat() / columnSize
    val columnMeasureStart get() = columnStart.toFloat() / columnSize
}