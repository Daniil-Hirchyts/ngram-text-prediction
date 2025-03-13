package models

class ConstructeurTrie:
  def construire[T](donnees: Map[String, Probabilites[T]]): Trie[T] =
    donnees.foldLeft(Trie[T]()) { case (trie, (mot, probs)) =>
      trie.inserer(mot, probs)
    }
