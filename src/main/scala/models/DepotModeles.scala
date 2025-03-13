package models

import java.nio.file.{Files, Path}
import java.sql.{Connection, DriverManager}
import scala.util.Using

/** Stockage SQLite. On conserve les comptages et non les probabilités : deux
  * comptages s'additionnent, ce qui rend possible l'enrichissement d'un modèle.
  */
final class DepotModeles(fichier: Path):
  import ModeleNGramme.{Comptages, ParContexte}

  private val url = s"jdbc:sqlite:${fichier.toAbsolutePath}"

  Option(fichier.toAbsolutePath.getParent).foreach(Files.createDirectories(_))
  avecConnexion { c =>
    Using.resource(c.createStatement()) { s =>
      s.execute(
        """CREATE TABLE IF NOT EXISTS modeles (
          |  id TEXT PRIMARY KEY,
          |  nom TEXT NOT NULL
          |)""".stripMargin
      )
      s.execute(
        """CREATE TABLE IF NOT EXISTS comptages (
          |  modele TEXT NOT NULL REFERENCES modeles(id) ON DELETE CASCADE,
          |  taille INTEGER NOT NULL,
          |  contexte TEXT NOT NULL,
          |  mot TEXT NOT NULL,
          |  occurrences INTEGER NOT NULL,
          |  PRIMARY KEY (modele, taille, contexte, mot)
          |) WITHOUT ROWID""".stripMargin
      )
    }
  }

  private def avecConnexion[A](f: Connection => A): A =
    Using.resource(DriverManager.getConnection(url)) { c =>
      Using.resource(c.createStatement())(_.execute("PRAGMA foreign_keys = ON"))
      f(c)
    }

  private def enTransaction[A](f: Connection => A): A =
    avecConnexion { c =>
      c.setAutoCommit(false)
      try
        val resultat = f(c)
        c.commit()
        resultat
      catch
        case e: Throwable =>
          c.rollback()
          throw e
    }

  def lister(): List[(String, String)] =
    avecConnexion { c =>
      Using.resource(c.createStatement()) { s =>
        Using.resource(
          s.executeQuery("SELECT id, nom FROM modeles ORDER BY id")
        ) { r =>
          Iterator
            .continually(r)
            .takeWhile(_.next())
            .map(l => (l.getString(1), l.getString(2)))
            .toList
        }
      }
    }

  /** Ajoute les comptages à ceux du modèle, en le créant au besoin. L'écriture
    * est atomique : une erreur laisse le modèle dans son état précédent.
    */
  def ajouter(id: String, nom: String, comptages: Comptages): Unit =
    enTransaction { c =>
      Using.resource(
        c.prepareStatement(
          "INSERT OR IGNORE INTO modeles(id, nom) VALUES (?, ?)"
        )
      ) { s =>
        s.setString(1, id)
        s.setString(2, nom)
        s.executeUpdate()
      }
      Using.resource(
        c.prepareStatement(
          """INSERT INTO comptages(modele, taille, contexte, mot, occurrences)
            |VALUES (?, ?, ?, ?, ?)
            |ON CONFLICT(modele, taille, contexte, mot)
            |DO UPDATE SET occurrences = occurrences + excluded.occurrences""".stripMargin
        )
      ) { s =>
        for
          (taille, contextes) <- comptages
          (contexte, mots) <- contextes
          (mot, occurrences) <- mots
        do
          s.setString(1, id)
          s.setInt(2, taille)
          s.setString(3, contexte)
          s.setString(4, mot)
          s.setInt(5, occurrences)
          s.addBatch()
        s.executeBatch()
      }
    }

  def charger(id: String): Comptages =
    avecConnexion { c =>
      Using.resource(
        c.prepareStatement(
          "SELECT taille, contexte, mot, occurrences FROM comptages WHERE modele = ?"
        )
      ) { s =>
        s.setString(1, id)
        Using.resource(s.executeQuery()) { r =>
          val lignes = Iterator
            .continually(r)
            .takeWhile(_.next())
            .map(l =>
              (l.getInt(1), l.getString(2), l.getString(3), l.getInt(4))
            )
            .toList
          lignes.groupBy(_._1).map { (taille, parTaille) =>
            val contextes: ParContexte = parTaille
              .groupBy(_._2)
              .map((contexte, ls) =>
                (contexte, ls.map(l => (l._3, l._4)).toMap)
              )
            (taille, contextes)
          }
        }
      }
    }

  def supprimer(id: String): Boolean =
    enTransaction { c =>
      Using.resource(c.prepareStatement("DELETE FROM modeles WHERE id = ?")) {
        s =>
          s.setString(1, id)
          s.executeUpdate() > 0
      }
    }
