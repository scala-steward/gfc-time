package com.gilt.gfc.time

import scala.concurrent.{ExecutionContext, Future}

/**
 * Various utilities for timing & reporting on blocks of code.
 *
 * Probably Timer.timePrettyFormat is the method you want; it is
 * easy to format the time into a human readable string to log.
 */
trait Timer {

  protected def nanoClock(): Long

  /**
   * Times the body, and passes the nanosecond time to the report function as a long.
   */
  def time[T](report: Long => Unit)(body: => T): T = {
    val start = nanoClock()
    val result = body
    report(nanoClock() - start)
    result
  }

  /**
   * Times the body, and passes the nanosecond time to the report function as a formatted
   * time string (including units, e.g. "372 ns"), generated by calling the pretty method.
   */
  def timePretty[T](report: String => Unit)(body: => T): T = {
    time(r => report(pretty(r)))(body)
  }

  /**
   * Times the body, then calls pretty on the elapsed time to get a more human-friendly time
   * string, then passes that to the "format" method.
   * For example:
   *   timePrettyFormat("This operation took %s", log.debug(_)) {
   *     Thread.sleep(1000)
   *   }
   *
   * Would log something close to "This operation took 1 s" to the debug logger.
   */
  def timePrettyFormat[T](format: String, report: String => Unit)(body: => T): T = {
    timePretty(pretty => report(format.format(pretty)))(body)
  }

  /**
   * Times the completion of the future, and passes the nanosecond time to the report function as a long.
   */
  def timeFuture[T](report: Long => Unit)(future: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val start = nanoClock()
    future.onComplete(_ => report(nanoClock()- start))
    future
  }

  /**
   * Times the completion of the future, and passes the nanosecond time to the report function as a formatted
   * time string (including units, e.g. "372 ns"), generated by calling the pretty method.
   */
  def timeFuturePretty[T](report: String => Unit)(future: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    timeFuture(r => report(pretty(r)))(future)
  }

  /**
   * Times the completion of the future, then calls pretty on the elapsed time to get a more human-friendly time
   * string, then passes that to the "format" method.
   */
  def timeFuturePrettyFormat[T](format: String, report: String => Unit)(future: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    timeFuturePretty(pretty => report(format.format(pretty)))(future)
  }

  private [this] val factors = {
    val MillisPerSecond = 1000L
    val MillisPerMinute = 60L * MillisPerSecond
    val MillisPerHour   = 60L * MillisPerMinute
    val MillisPerDay    = 24L * MillisPerHour
    List(MillisPerDay, MillisPerHour, MillisPerMinute, MillisPerSecond)
  }

  /**
   * Turns a nanosecond time into a human-readable string, like "37 us" or "45 days 08:55:01".
   */
  def pretty(duration: Long): String = {
    (duration, duration / 1000L, duration / (1000L * 1000L), duration / (1000*1000*1000L)) match {
      case (ns, 0, 0, 0)  => "%d ns".format(ns)
      case (ns, us, 0, 0) if (ns == us * 1000) =>  "%d us".format(us)
      case (ns, us, 0, 0) => "%3.3f us".format(ns / 1000d)
      case (_, us, ms, 0) if us == ms * 1000 => "%d ms".format(ms)
      case (_, us, ms, 0) => "%3.3f ms".format(us / 1000d)
      case (_, _, ms, s) if s < 60 && ms == s * 1000 => "%d s".format(s)
      case (_, _, ms, s) if s < 60 => "%3.3f s".format(ms / 1000d)
      case (_, _, ms, _) =>
        factors.foldLeft((ms, List.empty[Long])) {
          case ((ms: Long, bits: List[_]), millisPer: Long) =>
            (ms % millisPer, (ms / millisPer) :: bits.asInstanceOf[List[Long]])
        }._2.reverse match {
          case List(0, h, m, s) =>
            "%02d:%02d:%02d".format(h, m, s)
          case List(d, h, m, s) =>
            "%d days %02d:%02d:%02d".format(d, h, m, s)
          case _ => "%d ms".format(ms)
        }
    }
  }
}

object Timer extends Timer {
  def nanoClock() = System.nanoTime()
}
