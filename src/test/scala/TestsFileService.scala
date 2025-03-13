import java.nio.charset.StandardCharsets
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import services.FileService

class TestsFileService extends AnyFlatSpec with Matchers:
  "decoder" should "lire l'UTF-8 et retirer la marque d'ordre des octets" in {
    FileService.decoder("﻿été".getBytes(StandardCharsets.UTF_8)) shouldBe "été"
  }

  it should "se rabattre sur Windows-1252 si le fichier n'est pas en UTF-8" in {
    FileService.decoder(
      Array(0x63, 0x61, 0x66, 0xe9).map(_.toByte)
    ) shouldBe "café"
  }

  "retirerGutenberg" should "ne garder que le corps du livre" in {
    val livre =
      "licence avant\n*** START OF THE PROJECT GUTENBERG EBOOK X ***\ncorps du livre\n*** END OF THE PROJECT GUTENBERG EBOOK X ***\nlicence après"
    FileService.retirerGutenberg(livre).trim shouldBe "corps du livre"
  }

  it should "laisser intact un texte sans marqueurs" in {
    FileService.retirerGutenberg("texte simple") shouldBe "texte simple"
  }

  "estFichierTexte" should "reconnaître l'extension sans tenir compte de la casse" in {
    FileService.estFichierTexte("a.TXT") shouldBe true
    FileService.estFichierTexte("a.pdf") shouldBe false
  }
