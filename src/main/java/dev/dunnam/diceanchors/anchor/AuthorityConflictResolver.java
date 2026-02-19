package dev.dunnam.diceanchors.anchor;

/**
 * Authority-tiered conflict resolver implementing a graduated conflict matrix.
 * <p>
 * Resolution decisions are based on the existing anchor's authority level and the
 * incoming proposition's confidence score:
 *
 * <table border="1">
 *   <caption>Conflict resolution matrix</caption>
 *   <tr><th>Existing Authority</th><th>Incoming Confidence</th><th>Resolution</th></tr>
 *   <tr><td>CANON</td><td>any</td><td>KEEP_EXISTING</td></tr>
 *   <tr><td>RELIABLE</td><td>&ge; 0.8</td><td>REPLACE</td></tr>
 *   <tr><td>RELIABLE</td><td>0.6 – 0.8</td><td>DEMOTE_EXISTING</td></tr>
 *   <tr><td>RELIABLE</td><td>&lt; 0.6</td><td>KEEP_EXISTING</td></tr>
 *   <tr><td>UNRELIABLE</td><td>&ge; 0.6</td><td>REPLACE</td></tr>
 *   <tr><td>UNRELIABLE</td><td>&lt; 0.6</td><td>DEMOTE_EXISTING</td></tr>
 *   <tr><td>PROVISIONAL</td><td>any</td><td>REPLACE</td></tr>
 * </table>
 *
 * <h2>Thread safety</h2>
 * This class is stateless and therefore thread-safe.
 */
public class AuthorityConflictResolver implements ConflictResolver {

    @Override
    public ConflictResolver.Resolution resolve(ConflictDetector.Conflict conflict) {
        var existingAuthority = conflict.existing().authority();
        var confidence = conflict.confidence();
        return switch (existingAuthority) {
            case CANON -> Resolution.KEEP_EXISTING;
            case RELIABLE -> {
                if (confidence >= 0.8) {
                    yield Resolution.REPLACE;
                } else if (confidence >= 0.6) {
                    yield Resolution.DEMOTE_EXISTING;
                } else {
                    yield Resolution.KEEP_EXISTING;
                }
            }
            case UNRELIABLE -> {
                if (confidence >= 0.6) {
                    yield Resolution.REPLACE;
                } else {
                    yield Resolution.DEMOTE_EXISTING;
                }
            }
            case PROVISIONAL -> Resolution.REPLACE;
        };
    }
}
