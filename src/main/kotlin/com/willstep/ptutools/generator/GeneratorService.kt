package com.willstep.ptutools.generator

import com.willstep.ptutools.core.EXPERIENCE_CHART
import com.willstep.ptutools.core.Nature
import com.willstep.ptutools.dataaccess.dto.Move
import com.willstep.ptutools.dataaccess.dto.PokedexEntry
import com.willstep.ptutools.dataaccess.dto.Pokemon
import com.willstep.ptutools.dataaccess.service.DataUpdater
import com.willstep.ptutools.dataaccess.service.FirestoreService
import java.util.stream.Collectors
import kotlin.random.Random


class GeneratorService(
    val firestoreService: FirestoreService = FirestoreService()
) {

    fun generatePokemon(pokedexEntry: PokedexEntry, minLevel: Int, maxLevel: Int, nature: Nature?, shinyOdds: Double): Pokemon {
        val level = Random.nextInt(minLevel, maxLevel + 1)

        return generatePokemon(pokedexEntry, level, nature, shinyOdds)
    }

    fun generatePokemon(pokedexEntry: PokedexEntry, level: Int, nature: Nature?, shinyOdds: Double): Pokemon {
        val pokemon = Pokemon(
            pokedexEntry = pokedexEntry,
            level = level,
            exp = EXPERIENCE_CHART[level-1],
            gender = if (pokedexEntry.genderless) "No Gender" else if (Random.nextDouble(100.0) <= pokedexEntry.malePercent ?: 0.0) "Male" else "Female"
        )

        DataUpdater().checkPokemonForUpdates(pokemon)

        applyNature(pokemon, nature ?: Nature.values()[Random.nextInt(Nature.values().size)])
        randomizeStats(pokemon)
        randomizeAbilities(pokemon)
        randomizeMoves(pokemon, level, if (pokemon.abilities.any { it.name == "Cluster Mind" }) 8 else 6)

        pokemon.shiny = Random.nextDouble(100.0) <= shinyOdds

        return pokemon
    }

    fun randomizeMoves(pokemon: Pokemon, level: Int, count: Int) {
        var filteredList: MutableList<String>

        if (pokemon.pokedexEntry.moveLearnset != null && pokemon.pokedexEntry.moveLearnset!!.levelUpMoves.isNotEmpty()) {
            filteredList = pokemon.pokedexEntry.moveLearnset!!.levelUpMoves.filter { m -> m.learnedLevel <= level }
                .stream().map(PokedexEntry.MoveLearnset.Entry::moveName).collect(Collectors.toList())
        } else {
            filteredList = pokemon.pokedexEntry.levelUpMoves.filterValues { i -> i <= level }.keys.toMutableList()
        }

        val results = ArrayList<String>()

        for (i in 1..count) {
            if (filteredList.isEmpty()) {
                break
            }
            val moveName = filteredList[Random.nextInt(filteredList.size)]
            filteredList.remove(moveName)
            results.add(moveName)
        }

        val moves = ArrayList<Move>()

        for (name in results) {
            val homebrew = pokemon.pokedexEntry.moveLearnset?.homebrewMoves?.find { it.name == name }
            if (homebrew != null) {
                moves.add(homebrew)
            } else {
                firestoreService.getDocument("moves", name).get().get().toObject(Move::class.java)?.let {
                    if (pokemon.pokedexEntry.types.contains(it.type)) {
                        it.stab = true
                        it.damageBase = it.damageBase?.plus(2)
                    }
                    moves.add(it)
                }
            }
        }

        pokemon.moves = moves
    }

    fun randomizeAbilities(pokemon: Pokemon) {
        val usedNames = ArrayList<String>()

        if (pokemon.pokedexEntry.abilityLearnset.basicAbilities.isEmpty()) {
            return
        }

        var ability = pokemon.pokedexEntry.abilityLearnset.basicAbilities[Random.nextInt(pokemon.pokedexEntry.abilityLearnset.basicAbilities.size)]
        pokemon.abilities.add(ability)
        usedNames.add(ability.name!!)

        if (pokemon.level >= 20) {
            val secondAbilities = (pokemon.pokedexEntry.abilityLearnset.basicAbilities + pokemon.pokedexEntry.abilityLearnset.advancedAbilities).toMutableList()
            secondAbilities.removeAll { a -> usedNames.contains(a.name) }
            ability = secondAbilities[Random.nextInt(secondAbilities.size)]
            pokemon.abilities.add(ability)
            usedNames.add(ability.name!!)
        }
        if (pokemon.level >= 40) {
            val thirdAbilities = (pokemon.pokedexEntry.abilityLearnset.basicAbilities + pokemon.pokedexEntry.abilityLearnset.advancedAbilities
                    + pokemon.pokedexEntry.abilityLearnset.highAbilities).toMutableList()
            thirdAbilities.removeAll { a -> usedNames.contains(a.name) }
            ability = thirdAbilities[Random.nextInt(thirdAbilities.size)]
            pokemon.abilities.add(ability)
            usedNames.add(ability.name!!)
        }
    }

    fun applyNature(pokemon: Pokemon, nature: Nature) {
        pokemon.nature = nature.label
        when (nature.raise) {
            "hp" -> pokemon.hp.base++
            "atk" -> pokemon.atk.base+=2
            "def" -> pokemon.def.base+=2
            "spatk" -> pokemon.spatk.base+=2
            "spdef" -> pokemon.spdef.base+=2
            "spd" -> pokemon.spd.base+=2
        }
        when (nature.lower) {
            "hp" -> pokemon.hp.base--
            "atk" -> pokemon.atk.base-=2
            "def" -> pokemon.def.base-=2
            "spatk" -> pokemon.spatk.base-=2
            "spdef" -> pokemon.spdef.base-=2
            "spd" -> pokemon.spd.base-=2
        }

        // All base stats must be minimum of 1
        if (pokemon.hp.base < 1) {
            pokemon.hp.base = 1
        }
        if (pokemon.atk.base < 1) {
            pokemon.atk.base = 1
        }
        if (pokemon.def.base < 1) {
            pokemon.def.base = 1
        }
        if (pokemon.spatk.base < 1) {
            pokemon.spatk.base = 1
        }
        if (pokemon.spdef.base < 1) {
            pokemon.spdef.base = 1
        }
        if (pokemon.spd.base < 1) {
            pokemon.spd.base = 1
        }
    }

    fun randomizeStats(pokemon: Pokemon) {
        val sortedBaseStats = mapOf(
            "hp" to pokemon.hp.base,
            "atk" to pokemon.atk.base,
            "def" to pokemon.def.base,
            "spatk" to pokemon.spatk.base,
            "spdef" to pokemon.spdef.base,
            "spd" to pokemon.spd.base,
        ).toList().sortedBy { (_, value) -> value }
        for (points in 1..10 + pokemon.level) {
            val canAddTo = ArrayList<String>()

            // Figure out what can be added to
            for (baseStat1 in sortedBaseStats) {
                var value1 = getStatByName(pokemon, baseStat1.first)
                var isAddable = true
                for (baseStat2 in sortedBaseStats) {
                    var value2 = getStatByName(pokemon, baseStat2.first)

                    if (baseStat1.second < baseStat2.second && value1 + 1 >= value2) {
                        isAddable = false
                    }
                }
                if (isAddable) {
                    canAddTo.add(baseStat1.first)
                }
            }

            incrementStatByName(pokemon, canAddTo[Random.nextInt(canAddTo.size)])
        }

        // Determine starting Health
        pokemon.health = pokemon.level + (pokemon.hp.getSum() * 3) + 10
    }

    private fun getStatByName(pokemon: Pokemon, statName: String): Int {
        return when (statName) {
            "hp" -> pokemon.hp.getSum()
            "atk" -> pokemon.atk.getSum()
            "def" -> pokemon.def.getSum()
            "spatk" -> pokemon.spatk.getSum()
            "spdef" -> pokemon.spdef.getSum()
            "spd" -> pokemon.spd.getSum()
            else -> 0
        }
    }

    private fun incrementStatByName(pokemon: Pokemon, statName: String) {
        when {
            statName == "hp" -> pokemon.hp.lvlUp++
            statName == "atk" -> pokemon.atk.lvlUp++
            statName == "def" -> pokemon.def.lvlUp++
            statName == "spatk" -> pokemon.spatk.lvlUp++
            statName == "spdef" -> pokemon.spdef.lvlUp++
            statName == "spd" -> pokemon.spd.lvlUp++
        }
    }
}