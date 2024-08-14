package org.example

import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ObjectLoader
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.io.File
import javax.mail.Message
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.util.Properties

fun main() = runBlocking {
    val projects = getGitProjects()

    projects.forEach { project ->
        val branches = getReleaseBranches(project)
        branches.forEach { branch ->
            val commits = getCommitsAfterCut(branch)
            val pendingChanges = filterExcludedChanges(branch, commits)

            pendingChanges.forEach { change ->
                createPatch(change)
                // Assuming you have a way to determine user responsible for the change
                sendEmail("user@example.com", "$change.patch")
            }
        }
    }
}

fun getGitProjects(): List<String> {
    // Example to get list of projects
    //todo: read from repo instead of this
    return listOf("project1", "project2")
}

fun getReleaseBranches(project: String): List<String> {
    val repo = openRepository(project)
    val git = Git(repo)
    return git.branchList().call()
        .filter { it.name.contains("release") }
        .map { it.name }
}

fun getCommitsAfterCut(branch: String): List<String> {
    val repo = openRepository(".")
    val git = Git(repo)
    val logs = git.log().add(repo.resolve(branch)).call()
    return logs.map { it.name }
}

fun filterExcludedChanges(branch: String, commits: List<String>): List<String> {
    val repo = openRepository(".")
    val git = Git(repo)
    val notes = git.notesList().call()

    return commits.filter { commit ->
        notes.none { note ->
            note.name == commit && isNullIntegrateNotePresent(repo, note.data)
        }
    }
}

private fun isNullIntegrateNotePresent(repo: Repository, objectId: ObjectId): Boolean {
    val loader: ObjectLoader = repo.open(objectId)
    val noteContent = String(loader.bytes)
    return noteContent.contains("null-integrate")
}

fun createPatch(commit: String) {
    val repo = openRepository(".")
    val git = Git(repo)

    val commitId = repo.resolve(commit)
    val diffs = git.diff()
        .setOldTree(getTreeIterator(repo, commitId))
        .call()

    val patchFile = File("$commit.patch")
    patchFile.bufferedWriter().use { writer ->
        diffs.forEach { diff ->
            writer.write(diff.toString())
            writer.newLine()
        }
    }
}

private fun getTreeIterator(repo: Repository, commitId: ObjectId): AbstractTreeIterator {
    val reader = repo.newObjectReader()
    val treeParser = CanonicalTreeParser()
    val tree = repo.parseCommit(commitId).tree
    treeParser.reset(reader, tree)
    return treeParser
}


fun sendEmail(user: String, patchFile: String) {
    val properties = Properties().apply {
        put("mail.smtp.host", "localhost")
    }
    val session = Session.getDefaultInstance(properties)

    val message = MimeMessage(session).apply {
        setFrom(InternetAddress("no-reply@example.com"))
        setRecipient(Message.RecipientType.TO, InternetAddress(user))
        subject = "Patch for Forward Merge"
        setText("Please apply the following patch: $patchFile")
    }

    Transport.send(message)
}

fun openRepository(project: String): Repository {
    return FileRepositoryBuilder()
        .setGitDir(File("$project/.git"))
        .readEnvironment()
        .findGitDir()
        .build()
}
