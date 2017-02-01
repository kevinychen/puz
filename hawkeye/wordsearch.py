#!/bin/python

from collections import Counter, defaultdict
import math, multiprocessing, pytesseract
from PIL import Image

# A value in [0, 768]. The higher the value, the more likely pixels will be treated as "dark".
DARK_LEVEL = 384

# The smaller the value, the more accurately the grid will be aligned with the word search
WAVELEN_GAP = 0.1

DICTIONARY_FILE = '/usr/share/dict/words'

CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'

NEIGHBORS = ((0, 1), (1, 1), (1, 0), (1, -1), (0, -1), (-1, -1), (-1, 0), (-1, 1))

def openImage(url):
    return Image.open(url).convert('RGB')

def isDark(rgb):
    return rgb[0] + rgb[1] + rgb[2] < DARK_LEVEL

def toBinaryImage(im):
    newIm = Image.new(im.mode, im.size)
    newIm.putdata([(0, 0, 0) if isDark(rgb) else (255, 255, 255) for rgb in im.getdata()])
    return newIm

def toAnnotatedImage(im, points):
    newIm = Image.new(im.mode, im.size)
    newIm.putdata(im.getdata())
    newPix = newIm.load()
    for x, y in points:
        newPix[x, y] = (255, 0, 0)
        for dx, dy in NEIGHBORS:
            newPix[x + dx, y + dy] = (255, 0, 0)
    return newIm

def inBounds((x, y), (w, h)):
    return x >= 0 and x < w and y >= 0 and y < h

def findBlobs(im):
    pix = im.load()
    visited = set()
    for x in range(im.size[0]):
        for y in range(im.size[1]):
            if (x, y) not in visited and isDark(pix[x,y]):
                q = [(x, y)]
                blob = set()
                while q:
                    x, y = q.pop()
                    if inBounds((x, y), im.size) and (x, y) not in blob and isDark(pix[x, y]):
                        blob.add((x, y))
                        for (dx, dy) in NEIGHBORS:
                            q.append((x + dx, y + dy))
                visited.update(blob)
                xs, ys = zip(*blob)
                yield blob, (sum(xs) / len(xs), sum(ys) / len(ys))

def findGrid(points):
    m = max([max(x, y) for x, y in points])
    def dotWithWave(v, l, o=0):
        return sum([v[i] * math.cos(2 * math.pi * (i - o) / l) for i in v]) * math.sqrt(l)

    dxs, dys = defaultdict(int), defaultdict(int)
    for (x1, y1) in points:
        for (x2, y2) in points:
            dxs[abs(x1 - x2)] += 1
            dys[abs(y1 - y2)] += 1
    ds = [WAVELEN_GAP * (r + 1) for r in range(int(m / 10 / WAVELEN_GAP))]
    dx = max(ds, key=lambda d: dotWithWave(dxs, d))
    dy = max(ds, key=lambda d: dotWithWave(dys, d))

    xs, ys = map(Counter, zip(*points))
    ox = max(range(int(dx)), key=lambda o: dotWithWave(xs, dx, o))
    oy = max(range(int(dy)), key=lambda o: dotWithWave(ys, dy, o))

    def score(sx, ex, sy, ey):
        coords = map(lambda (x, y): ((x - ox) / dx, (y - oy) / dy), points)
        return sum([math.cos(2 * math.pi * x) + math.cos(2 * math.pi * y) \
                for x, y in coords if x >= sx and x <= ex and y >= sy and y <= ey]) / math.sqrt((ex - sx) * (ey - sy))
    ex = max(range(int((m - ox) / dx)), key=lambda e: score(0, e + .5, 0, m / dy))
    sx = max(range(ex), key=lambda s: score(s - .5, ex + .5, 0, m / dy))
    ey = max(range(int((m - oy) / dy)), key=lambda e: score(sx - .5, ex + .5, 0, e + .5))
    sy = max(range(ey), key=lambda s: score(sx - .5, ex + .5, s - .5, ey + .5))

    return ex - sx + 1, ey - sy + 1, ox + dx * sx, oy + dy * sy, dx, dy

def findBlob((x, y), blobs, grid):
    w, h, ox, oy, dx, dy = grid
    return min(blobs, key=lambda (blob, (cx, cy)): abs((ox + x * dx) - cx) + abs((oy + y * dy) - cy))

def findLetters(im, blobs, grid):
    w, h, ox, oy, dx, dy = grid
    def getRowImage(ly):
        rowBlobs = [findBlob((lx, ly), blobs, grid) for lx in range(w)]
        xs, ys = zip(*[(x, y) for (blob, (cx, cy)) in rowBlobs for (x, y) in blob])
        minX, maxX, minY, maxY = min(xs) - 3, max(xs) + 3, min(ys) - 3, max(ys) + 3
        newIm = Image.new(im.mode, (maxX - minX, maxY - minY))
        newPix = newIm.load()
        for x in range(minX, maxX):
            for y in range(minY, maxY):
                newPix[x - minX, y - minY] = (255, 255, 255)
        for blob, (cx, cy) in rowBlobs:
            for x, y in blob:
                newPix[x - minX, y - minY] = (0, 0, 0)
        return newIm
    config = '-psm 7 -c tessedit_char_whitelist="%s" -c load_system_dawg=0 -c load_freq_dawg=0' % CHARS
    return w, h, [pytesseract.image_to_string(getRowImage(y), config=config) for y in range(h)]

def getDictionary():
    return set([word.upper() for word in open(DICTIONARY_FILE).read().splitlines()])

def wordsearch(letters, dictionary):
    w, h, grid = letters
    for l in range(max(w, h), 1, -1):
        for x in range(w):
            for y in range(h):
                for dx, dy in NEIGHBORS:
                    if inBounds((x + (l - 1) * dx, y + (l - 1) * dy), (w, h)):
                        coords = [(x + d * dx, y + d * dy) for d in range(l)]
                        word = ''.join(map(lambda (x, y): grid[y][x], coords))
                        if word in dictionary:
                            yield coords, word

def toAnnotatedBlobImage(im, coords, blobs, grid):
    newIm = Image.new(im.mode, im.size)
    newIm.putdata(im.getdata())
    newPix = newIm.load()
    for coord in coords:
        blob, (cx, cy) = findBlob(coord, blobs, grid)
        for x, y in blob:
            newPix[x, y] = (255, 128, 0)
            for dx, dy in NEIGHBORS:
                newPix[x + dx, y + dy] = (255, 128, 0)
    return newIm

im = openImage('/Users/kchen/Downloads/wordsearch.jpg')
im = toBinaryImage(im)
blobs = list(findBlobs(im))
_, points = zip(*blobs)
grid = findGrid(points)
letters = findLetters(im, blobs, grid)
for index, (coords, word) in enumerate(wordsearch(letters, getDictionary())):
    print index, word
    toAnnotatedBlobImage(im, coords, blobs, grid).save('output/result_%3s.png' % index)

