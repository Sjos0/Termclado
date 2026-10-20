package com.example.keyboard

import android.content.Context
import java.io.File

/**
 * Instâncias únicas do aprendizado no processo. O teclado e a tela de configurações usam as mesmas,
 * então apagar pelas configurações também limpa a memória do serviço (senão ele regravaria os dados).
 */
object LearningStore {
    @Volatile private var dictionary: UserDictionary? = null
    @Volatile private var bigrams: PersonalBigrams? = null

    fun dictionary(context: Context): UserDictionary =
        dictionary ?: synchronized(this) {
            dictionary ?: UserDictionary(File(context.applicationContext.filesDir, "user_dict.tsv")).also { dictionary = it }
        }

    fun bigrams(context: Context): PersonalBigrams =
        bigrams ?: synchronized(this) {
            bigrams ?: PersonalBigrams(File(context.applicationContext.filesDir, "bigrams.tsv")).also { bigrams = it }
        }

    fun clearAll(context: Context) {
        dictionary(context).clear()
        bigrams(context).clear()
    }
}
