package com.willstep.ptutools

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.willstep.ptutools.core.PTUCoreInfoService
import com.willstep.ptutools.dataaccess.dto.*
import com.willstep.ptutools.dataaccess.service.DataUpdater
import com.willstep.ptutools.dataaccess.service.EvolutionsRemaining
import com.willstep.ptutools.dataaccess.service.FirestoreService
import com.willstep.ptutools.dataaccess.service.UploadDataTool
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.thymeleaf.util.StringUtils
import java.io.ByteArrayInputStream
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


@RestController
class PtuToolsRestController {

//    @PostMapping("/uploadMoves")
    fun uploadMovesOld(@RequestBody movesList: List<Map<String, Any>>, model: Model) {
        UploadDataTool().uploadMoveArray(movesList)
    }

//    @PostMapping("/uploadMoves")
    fun uploadMoves(@RequestBody movesList: List<Move>, model: Model) {
        for (move in movesList) {
            FirestoreService().saveAsDocument("moves", move.name!!, move)
        }
    }

//    @PostMapping("/uploadAbilities")
    fun uploadAbilities(@RequestBody abilities: List<Ability>, model: Model) {
        for (ability in abilities) {
            FirestoreService().saveAsDocument("abilities", ability.name!!, ability)
        }
    }

//    @PostMapping("/uploadPokedex")
    fun uploadPokedex(@RequestBody dexEntries: List<Map<String, Any>>, model: Model): String {
        UploadDataTool().uploadPokedexEntries(dexEntries)

        return dexEntries.size.toString() + " Entries Uploaded"
    }

//    @PostMapping("/uploadPokedexEntriesFromPdfText")
    fun uploadPokedexEntriesFromPdfText(@RequestPart content: String, @RequestParam region: String): String {
        UploadDataTool().uploadPokedexEntriesFromPdfText(content.split("\n"), region)

        return "Entries Uploaded"
    }

//    @PostMapping("/uploadEvolutionsRemaining")
    fun uploadEvolutionsRemaining(@RequestBody entries: List<EvolutionsRemaining>, model: Model): String {
        UploadDataTool().uploadEvolutionsRemaining(entries)

        return entries.size.toString() + " Entries Uploaded"
    }

//    @PostMapping("/uploadMoveLearnset")
    fun uploadMoveLearnset(@RequestBody entries: Map<String, Map<String, List<String>>>, model: Model): String {
        UploadDataTool().uploadMoveLearnset(entries)

        return entries.size.toString() + " Entries Uploaded"
    }

//    @PostMapping("/uploadPokeEdges")
    fun uploadPokeEdges(@RequestBody entries: List<Map<String, String>>, model: Model): String {
        UploadDataTool().uploadPokeEdges(entries)

        return entries.size.toString() + " Entries Uploaded"
    }

//    @PostMapping("/uploadPokemonEvolutionData")
    fun uploadPokemonEvolutionData(@RequestBody entries: List<List<String>>, model: Model): String {
        UploadDataTool().uploadEvolutionData(entries)

        return entries.size.toString() + " Entries Uploaded"
    }

    @PostMapping("/populatePokedexWithPokeAPI")
    fun populatePokedexWithPokeAPI(@RequestBody entries: List<PokedexEntry>, model: Model): List<PokedexEntry> {
        UploadDataTool().populatePokedexWithPokeAPI(entries)
        return entries
    }

//    @PostMapping("/overengineeredHabitatParsing")
    fun overengineeredHabitatParsing(@RequestBody pokedexMess: String, model: Model): String {
        var output = ""
        val entries = pokedexMess.split(" Unofficial Homebrew ")

        for (entry in entries) {
            val name = entry.substring(0, entry.indexOf("Base Stats")).trim()
            val habitats = entry.substring(entry.indexOf("Habitat:") + 8, entry.indexOf("Capabilities")).trim()
            output += "$name: $habitats\n"
        }
        return output
    }

    @GetMapping("/pokedex/{dexNumber}")
    fun getPokedexEntryByNumber(@PathVariable dexNumber: String, model: Model) : List<PokedexEntry?> {
        if (dexNumber.length > 3) {
            val entry = FirestoreService().getDocument("pokedexEntries", dexNumber).get().get().toObject(PokedexEntry::class.java)
            if (entry != null) {
                DataUpdater().checkPokedexEntryForUpdates(entry)
            }
            return listOf(entry)
        } else {
            return FirestoreService().getCollection("pokedexEntries").whereEqualTo("nationalDexNumber", dexNumber).get().get().toObjects(PokedexEntry::class.java)
        }
    }

    @GetMapping("/move/{moveName}")
    fun getMoveByName(@PathVariable moveName: String, model: Model): ResponseEntity<Move> {
        return ResponseEntity.ok(FirestoreService().getDocument("moves", moveName).get().get().toObject(Move::class.java))
    }

    @GetMapping("/ability/{abilityName}")
    fun getAbilityByName(@PathVariable abilityName: String, model: Model): ResponseEntity<Ability> {
        return ResponseEntity.ok(FirestoreService().getDocument("abilities", abilityName).get().get().toObject(Ability::class.java))
    }

    @GetMapping("/pokeedge/{pokeEdgeName}")
    fun getPokeEdgeByName(@PathVariable pokeEdgeName: String, model: Model): ResponseEntity<PokeEdge> {
        return ResponseEntity.ok(FirestoreService().getDocument("pokeEdges", pokeEdgeName).get().get().toObject(PokeEdge::class.java))
    }

    @DeleteMapping("/pokemon/{pokemonId}")
    fun deletePokemon(@PathVariable pokemonId: String, model: Model): ResponseEntity<String> {
        val pokemon = FirestoreService().getDocument("pokemon", pokemonId).delete()

        return ResponseEntity.ok("Deleted 1 Entry")
    }

    @GetMapping("/typeEffectivity")
    fun getTypeEffectivity(@RequestParam types: List<String>, model: Model): ResponseEntity<Map<String, Double>> {
        val typeList = ArrayList<Type>()

        try {
            for (type in types) {
                typeList.add(Type.valueOf(type.toUpperCase()))
            }
        } catch (e: Exception) {
            return ResponseEntity(HttpStatus.BAD_REQUEST)
        }

        return ResponseEntity.ok(PTUCoreInfoService().getTypeEffectivity(typeList).toList()
            .map { pair -> Pair(pair.first.displayName, pair.second) } .sortedBy { pair -> pair.second * -1.0 } .toMap())
    }

    @GetMapping("/calculateDamage")
    fun getCalculateDamage(@RequestParam targetTypes: List<String>, @RequestParam targetDefense: Int, @RequestParam attackType: String, @RequestParam attackAmount: Int) : ResponseEntity<Int> {
        try {
            val typeList = ArrayList<Type>()
            val atkType = Type.valueOf(attackType.toUpperCase())

            for (type in targetTypes) {
                typeList.add(Type.valueOf(type.toUpperCase()))
            }

            return ResponseEntity.ok(PTUCoreInfoService().calculateDamage(typeList, targetDefense, atkType, attackAmount))

        } catch (e: Exception) {
            return ResponseEntity(HttpStatus.BAD_REQUEST)
        }
    }

    @GetMapping("/levelUpPokemon")
    fun checkPokemonForLevelUp(@RequestParam pokedexEntryDocumentId: String?, @RequestParam currentLevel: Int, @RequestParam exp: Int): LevelUpChanges {
        return PTUCoreInfoService().levelUpPokemon(pokedexEntryDocumentId, currentLevel, exp)
    }

    @PostMapping("/savePokemonToFile")
    fun savePokemonToFile(@ModelAttribute pokemon: Pokemon): ResponseEntity<InputStreamResource> {
        pokemon.cleanup()
        pokemon.googleDriveFileId = null
        val buf: ByteArray = ObjectMapper().writeValueAsBytes(pokemon)

        return ResponseEntity
            .ok()
            .contentLength(buf.size.toLong())
            .contentType(
                MediaType.parseMediaType("application/octet-stream")
            )
            .header("Content-Disposition", String.format("attachment; filename=\"%s.json\"", pokemon.name))
            .body(InputStreamResource(ByteArrayInputStream(buf)))
    }

    @PostMapping("/savePokemonToGoogleDrive")
    fun savePokemonToGoogleDrive(@ModelAttribute pokemon: Pokemon, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<String> {
        return doSaveToGoogleDrive(pokemon, request, false)
    }

    @PostMapping("/uploadPokemonToGoogleDrive")
    fun uploadPokemonToGoogleDrive(@ModelAttribute pokemon: Pokemon, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<String> {
        return doSaveToGoogleDrive(pokemon, request, true)
    }

    private fun doSaveToGoogleDrive(pokemon: Pokemon, request: HttpServletRequest, isNew: Boolean): ResponseEntity<String> {
        pokemon.cleanup()
        val buf: ByteArray = ObjectMapper().writeValueAsBytes(pokemon)

        val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()

        val token = request.cookies.find { it.name == "authToken" }?.value

        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val driveService: Drive = Drive.Builder(
            HTTP_TRANSPORT, JacksonFactory.getDefaultInstance(), HttpCredentialsAdapter(
                GoogleCredentials.create(
                    AccessToken(token, null)
                )
            )
        )
            .setApplicationName("PokéSheets")
            .build()

        val file = File()
        file.name = pokemon.fileName ?: pokemon.name
        file.description = "PokéSheets Pokemon"
        file.mimeType = "application/json"

        if (!isNew) {
            driveService.files()
                .update(pokemon.googleDriveFileId, file, InputStreamContent(null, ByteArrayInputStream(buf))).execute()
        } else {
            val newFile: Any
            if (!StringUtils.isEmpty(pokemon.googleDriveFolderId)) {
                file.parents = Collections.singletonList(pokemon.googleDriveFolderId)
                pokemon.googleDriveFileId = null
                newFile = driveService.files().create(file, InputStreamContent(null, ByteArrayInputStream(buf)))
                    .setFields("id, parents")
                    .execute()
            } else {
                newFile = driveService.files().create(file, InputStreamContent(null, ByteArrayInputStream(buf)))
                    .execute()
            }
            return ResponseEntity.ok(newFile.id)
        }

        return ResponseEntity.ok().build()
    }
}