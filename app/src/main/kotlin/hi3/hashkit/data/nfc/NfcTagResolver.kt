package hi3.hashkit.data.nfc

import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.tag.MinerTag
import hi3.hashkit.domain.tag.MinerTagMatcher
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves a scanned NFC-tag payload to a navigation [NfcRouter.Target]. */
@Singleton
class NfcTagResolver @Inject constructor(
    private val repository: MinerRepository,
) {
    suspend fun resolve(payload: String): NfcRouter.Target {
        val tag = MinerTag.parse(payload) ?: return NfcRouter.Target.Overlay(payload)
        val candidates = repository.observeMinerEntities().first().map { e ->
            MinerTagMatcher.Candidate(e.id, e.name, e.host, e.macAddress, e.serialNumber)
        }
        val hit = MinerTagMatcher.match(tag, candidates)
        return if (hit != null) NfcRouter.Target.MinerDetail(hit.id)
        else NfcRouter.Target.Overlay(payload)
    }
}
