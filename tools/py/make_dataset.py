import sys, time
sys.path.insert(0, 'py')
import numpy as np
from mathai.render import build_dataset

per_class = int(sys.argv[1]) if len(sys.argv) > 1 else 1500
seed = int(sys.argv[2]) if len(sys.argv) > 2 else 1234
out = sys.argv[3] if len(sys.argv) > 3 else 'data/glyphs.npy'

t = time.time()
X, y = build_dataset(per_class=per_class, seed=seed, verbose=False)
np.save(out, X)
np.save(out.replace('.npy', '_y.npy'), y)
print("saved %s X=%s y=%s in %.1fs" % (out, X.shape, y.shape, time.time() - t))
