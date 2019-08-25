package com.rocketsolutions.render
import java.awt.{BasicStroke, Color, Font, Graphics2D, RenderingHints}
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.net.URL
import java.util.concurrent.TimeUnit

import com.rocketsolutions.model._
import javax.imageio.ImageIO
import zio.{Task, UIO, ZIO}
import zio.duration._

import scala.annotation.tailrec

case class HeadAndText(title: String, text: String)

object RendererSupport {

  final val FPS = 32
  final val font = new Font("8BIT WONDER", Font.PLAIN, 10)


  def gifTimeDecoder(g: GifPlay): UIO[Duration] = for {
    frames <- GifLoader.open(g.name).orDie
    time = frames.foldLeft(0 second)((t,f) => t + f.delay)
  } yield (time)

  def calculateClipTime(clip: Clip, gifCalc: GifPlay => UIO[Duration]): UIO[Option[Duration]] = clip match {
    case c: GifPlay if (c.duration == Length) => gifCalc(c).map(t => Some(t))
    case c => c.duration match {
      case Length => UIO.succeed(None)
      case Fit => UIO.succeed(None)
      case Timed(time) => UIO.succeed(Some(time))
    }
  }

  def calculateClipTotalTime(clips: Seq[Clip], gifCalc: GifPlay => UIO[Duration]): UIO[Seq[ClipWithTime]] = {
    ZIO.traverse(clips)(clip =>
      calculateClipTime(clip, gifTimeDecoder).map(t =>
        ClipWithTime(clip, t.map(_ * PlaySchedule.toCount(clip.play)))))
  }


  def renderText(g: Graphics2D, x: Int, y: Int, text: String, font: Font, alpha: Int) = {
    if (text != null && !text.isEmpty) {
      val title   = new TextLayout(text, font, g.getFontRenderContext)
      val outline = title.getOutline(null)

      g.translate(x, y)
      g.setStroke(
        new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER))
      g.setColor(new Color(0, 0, 0, alpha))
      g.draw(outline)
      g.setColor(new Color(255, 255, 255, alpha))
      g.fill(outline)
    }
  }

  def drawSolid(dim: Dimension, color: Color): BufferedImage = {
    val img = new BufferedImage(
      dim.width,
      dim.height,
      BufferedImage.TYPE_INT_ARGB)
    val g = img.getGraphics
    g.setColor(color)
    g.fillRect(0,0,dim.width,dim.height)
    g.dispose()
    img
  }

  def drawText(dim: Dimension, text: HeadAndText, alpha: Int): BufferedImage = {
    val img = new BufferedImage(
      dim.width,
      dim.height,
      BufferedImage.TYPE_INT_ARGB)
    val g = img.getGraphics.asInstanceOf[Graphics2D]

    g.setFont(font)

    val fm = g.getFontMetrics
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT)


    val textWidth = Math.max(fm.stringWidth(text.title), fm.stringWidth(text.text))
    val x = (img.getWidth / 2) - (textWidth / 2)
    val y = ((img.getHeight) / 2) - 2


    renderText(g, x, y, text.title, font, alpha)
    renderText(g, 0, fm.getHeight + 2, text.text, font, alpha)

    g.dispose()
    img
  }

  def mergeImages(top: Option[BufferedImage], bg: Option[BufferedImage]): BufferedImage = {
    val width = Math.max(top.map(_.getWidth).getOrElse(0),bg.map(_.getWidth).getOrElse(0))
    val height = Math.max(top.map(_.getHeight).getOrElse(0),bg.map(_.getHeight).getOrElse(0))
    val combined =
      new BufferedImage(width, height, BufferedImage.TYPE_USHORT_565_RGB)
    val g = combined.getGraphics

    bg.map(b => g.drawImage(b, 0, 0, null))
    top.map(t => g.drawImage(t, 0, 0, null))

    g.dispose
    combined
  }

  def renderGifFrames(duration: Duration, frames: Seq[Frame]): Seq[BufferedImage] = {
    val totalFrames = timeToFrames(duration)
    @tailrec
    def run(currentFrame: Int, in: List[Frame], res: Seq[BufferedImage]): Seq[BufferedImage] = in match {
        case Nil =>
          if (currentFrame < totalFrames) {
            run(currentFrame, frames.toList, res)
          } else {
            res
          }
        case h :: t =>
          if (currentFrame >= totalFrames) {
            res
          } else {
            val fpms = 1000f / FPS.toFloat
            val count = (Math.max(1,Math.floor(h.delay.toMillis / fpms)).toInt)
            run(currentFrame + count, t, res ++ Seq.fill(count)(h.img565))
          }
      }
    val l = run(0, frames.toList, Seq.empty)
    l
  }

  def centerImage(dim: Dimension, pic: BufferedImage): BufferedImage = {
    val out =
      new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_USHORT_565_RGB)

    out.getGraphics.drawImage(
      pic,
      -((pic.getWidth / 2) - (dim.width / 2)),
      -((pic.getHeight / 2) - (dim.height / 2)),
      null)

    out
  }

  def renderAndDownloadImage(dim: Dimension, url: URL): UIO[BufferedImage] =
    ZIO.effect(centerImage(dim, ImageIO.read(url))).orDie



  def timeToFrames(total: Duration) : Int =
    ((FPS * total.toMillis) / 1000).toInt

  def renderClip(dim: Dimension, cwt: ClipWithFinalTime): UIO[Seq[BufferedImage]] = {
    cwt.clip match {
      case Pause =>
        ZIO.succeed(Seq.fill(timeToFrames(cwt.time))(drawSolid(dim,new Color(0,0,0,0))))
      case Solid(color, _, _) =>
        ZIO.succeed(Seq.fill(timeToFrames(cwt.time))(drawSolid(dim,color)))
      case GifPlay(name, _, _)   =>
        GifLoader.open(name).map(frames => renderGifFrames(cwt.time, frames)).orDie
      case TwoHeader(title, sub, _, _) =>
        ZIO.succeed(Seq.fill(timeToFrames(cwt.time))(drawText(dim, HeadAndText(title,sub), 255)))
      case WebImage(url, _, _) =>
        renderAndDownloadImage(dim, url).map(img => Seq.fill(timeToFrames(cwt.time))(img))
    }
  }

  def totalTime(s: Seq[ClipWithTime]) = s.foldLeft(0 seconds)((total,c) => total + c.time.getOrElse(0 seconds))

  def toFinalTime(maxDuration: Duration, s: Seq[ClipWithTime]) = {
    val fitClips = s.count(_.time == None)
    val definedClipTime = s.flatMap(_.time).map(_.toMillis).sum
    val fitClipLength = Duration((maxDuration.toMillis - definedClipTime) / Math.max(1,fitClips), TimeUnit.MILLISECONDS)
    s.map(c => c.time match {
      case Some(d) => ClipWithFinalTime(c.clip, d)
      case None => ClipWithFinalTime(c.clip, fitClipLength)
    })
  }

  def renderFullScene(scene: FullScene): UIO[Seq[BufferedImage]] = for {
   fg <- calculateClipTotalTime(scene.fg,gifTimeDecoder)
   bg <- calculateClipTotalTime(scene.bg,gifTimeDecoder)
   maxTime: Duration = Duration(Math.max(totalTime(fg).toMillis, totalTime(bg).toMillis),TimeUnit.MILLISECONDS)
   start  <- ZIO.succeed(System.currentTimeMillis())
   fgFrames <- ZIO.traverse(toFinalTime(maxTime, fg))(c => renderClip(scene.dim, c)).map(_.flatten)
   bgFrames <- ZIO.traverse(toFinalTime(maxTime, bg))(c => renderClip(scene.dim, c)).map(_.flatten)
   finalFrames = fgFrames.map(Some(_))
     .zipAll(bgFrames.map(Some(_)), None, None)
     .map{
       case (f,b) => mergeImages(f,b)
     }
  } yield (finalFrames)
}
