import numpy as np

def mode_filter(seq, window=5):
    seq = np.asarray(seq)
    half = window // 2
    padded = np.pad(seq, (half, half), mode="edge")
    out = np.empty_like(seq)
    for i in range(len(seq)):
        vals, counts = np.unique(padded[i:i + window], return_counts=True)
        out[i] = vals[np.argmax(counts)]
    return out

def viterbi_smooth(probs, transition_matrix, init_probs=None):
    """
    HMM Viterbi smoothing to enforce temporal consistency.
    """
    T, K = probs.shape
    logA = np.log(transition_matrix + 1e-12)
    logB = np.log(np.clip(probs, 1e-12, 1.0))

    if init_probs is None:
        init_probs = np.full(K, 1.0 / K)
    logpi = np.log(init_probs + 1e-12)

    delta = np.zeros((T, K))
    psi = np.zeros((T, K), dtype=np.int32)
    delta[0] = logpi + logB[0]

    for t in range(1, T):
        for k in range(K):
            scores = delta[t - 1] + logA[:, k]
            psi[t, k] = np.argmax(scores)
            delta[t, k] = np.max(scores) + logB[t, k]

    path = np.zeros(T, dtype=np.int32)
    path[-1] = np.argmax(delta[-1])
    for t in range(T - 2, -1, -1):
        path[t] = psi[t + 1, path[t + 1]]
    return path

def apply_all_smoothings(y_raw, probs, transition_matrix, mode_window=5):
    """
    Apply multiple smoothing strategies.
    Note: y_raw should be argmax of probs.
    """
    return {
        "raw": y_raw,
        "mode": mode_filter(y_raw, window=mode_window),
        "viterbi": viterbi_smooth(probs, transition_matrix),
    }
