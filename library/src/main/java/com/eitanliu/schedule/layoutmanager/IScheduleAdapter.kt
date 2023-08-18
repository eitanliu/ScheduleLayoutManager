package com.eitanliu.schedule.layoutmanager


interface IScheduleAdapter {

    val scheduleItems: Iterable<IScheduleItem>

    fun getScheduleItem(position: Int): IScheduleItem
}