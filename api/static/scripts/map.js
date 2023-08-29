const canvas = document.getElementsByTagName('canvas')[0];

let currentServer = -1;
let currentDimension = "";
let ctx;

let originX;
let originZ;

window.onload = async function () {
    const cache = new Map();
    const targetResolution = 256;
    let jsonDimensionData = null;
    const qualityOffset = 0.0;

    function getScale(zoom) {
        return Math.max(1, Math.min(16, 2 ** Math.floor(Math.log2(zoom) + qualityOffset)));
    }

    const chunkMetaCache = new Map();
    const chunkMetaRequested = new Set();
    const CHUNKS_PER_REGION = 16;

    function getChunkMeta(x, z) {
        let cx = Math.floor(x / CHUNKS_PER_REGION);
        let cz = Math.floor(z / CHUNKS_PER_REGION);
        let id = cx + "_" + cz;
        if (!chunkMetaRequested.has(id)) {
            chunkMetaRequested.add(id)
            let url = "/v1/chunk_meta/" + currentServer + "/" + currentDimension + "?x=" + cx * CHUNKS_PER_REGION + "&z=" + cz * CHUNKS_PER_REGION + "&w=" + CHUNKS_PER_REGION + "&h=" + CHUNKS_PER_REGION;
            new Promise(async () => {
                let chunks = await fetchJson(url);
                for (let chunkJson of chunks) {
                    id = chunkJson["x"] + "_" + chunkJson["z"];
                    chunkMetaCache.set(id, chunkJson["meta"]);
                }
                redraw();
            });
        }
        id = x + "_" + z;
        return chunkMetaCache.has(id) ? chunkMetaCache.get(id) : {}
    }

    function getImage(x, y, tileSize, scale, load_image = false) {
        const tileCount = tileSize / 16;
        const path = "/v1/chunk/" + currentServer + "/" + currentDimension + "?x=" + (x * tileCount) + "&z=" + (y * tileCount) + "&w=" + tileCount + "&h=" + tileCount + "&scale=" + scale
        if (!cache.has(path)) {
            if (load_image) {
                let img = new Image()
                img.src = path
                img.addEventListener('load', function () {
                    redraw();
                })
                cache.set(path, img)
                return img;
            } else {
                return null;
            }
        }
        return cache.get(path)
    }

    function getAvatar(uuid, size) {
        uuid = uuid.replace("-", "")
        const path = "https://crafatar.com/avatars/" + uuid + "?size=" + size + "&default=MHF_Steve&overlay"
        if (!cache.has(path)) {
            let img = new Image()
            img.src = path
            img.addEventListener('load', function () {
                redraw();
            })
            cache.set(path, img)
            return img;
        }
        return cache.get(path)
    }

    const zoom = function (clicks) {
        const pt = ctx.transformedPoint(lastX, lastY);
        ctx.translate(pt.x, pt.y);
        const factor = Math.pow(scaleFactor, clicks);
        ctx.scale(factor, factor);
        ctx.translate(-pt.x, -pt.y);
        redraw();
    };
    ctx = canvas.getContext("2d", {alpha: false});
    trackTransforms(ctx);

    ctx.imageSmoothingEnabled = false;

    addEventListener("resize", (event) => {
        redraw()
    });

    function getRange(p1, p2, s, max) {
        let sx = Math.floor(p1.x / s);
        let ex = Math.ceil(p2.x / s);

        let sy = Math.floor(p1.y / s);
        let ey = Math.ceil(p2.y / s);

        // Limit max tiles
        while ((ex - sx) * (ey - sy) > max) {
            if ((ex - sx) > (ey - sy)) {
                sx++;
                ex--;
            } else {
                sy++;
                ey--;
            }
        }

        return [sx, sy, ex, ey]
    }

    function redraw() {
        let targetWidth = window.innerWidth - 20;
        let targetHeight = window.innerHeight - 120;
        if (canvas.width !== targetWidth || canvas.height !== targetHeight) {
            canvas.width = targetWidth;
            canvas.height = targetHeight;
            ctx.imageSmoothingEnabled = false;
            ctx.resetTransform();
            ctx.translate(canvas.width / 2 - originX, canvas.height / 2 - originZ);
        }

        const p1 = ctx.transformedPoint(0, 0);
        const p2 = ctx.transformedPoint(canvas.width, canvas.height);
        const p3 = ctx.transformedPoint(1, 1);

        // Clear the entire canvas
        ctx.save();
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.fillStyle = "#d5be95";
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.restore();

        let zoom = Math.sqrt((p3.x - p1.x) ** 2 + (p3.y - p1.y) ** 2);
        let tileSize = targetResolution * getScale(zoom);

        let e = getRange(p1, p2, tileSize, 100);
        let sx = e[0], sy = e[1], ex = e[2], ey = e[3];

        // Limit max tiles
        while ((ex - sx) * (ey - sy) > 100) {
            if ((ex - sx) > (ey - sy)) {
                sx++;
                ex--;
            } else {
                sy++;
                ey--;
            }
        }

        let margin = zoom;

        // Render tiles
        for (let x = sx; x < ex; x++) {
            for (let y = sy; y < ey; y++) {
                const scale = getScale(zoom);
                let img = getImage(x, y, tileSize, scale, true);
                if (img != null && img.complete && img.naturalHeight > 2) {
                    ctx.drawImage(img, x * tileSize, y * tileSize, tileSize + margin, tileSize + margin);
                } else {
                    // use lower resolution
                    let rx = Math.floor(x / 2);
                    let ry = Math.floor(y / 2);
                    let scale = getScale(zoom * 2)
                    let img = getImage(rx, ry, tileSize * 2, scale)
                    if (img != null && img.complete && img.naturalHeight > 2) {
                        ctx.drawImage(img, (x - rx * 2) * tileSize / scale, (y - ry * 2) * tileSize / scale, tileSize / scale, tileSize / scale, x * tileSize, y * tileSize, tileSize + margin, tileSize + margin);
                    } else {
                        // use higher resolution
                        for (let sx = 0; sx < 2; sx++) {
                            for (let sy = 0; sy < 2; sy++) {
                                const scale = getScale(zoom * 0.5);
                                let img = getImage(x * 2 + sx, y * 2 + sy, tileSize / 2, scale)
                                if (img != null && img.complete && img.naturalHeight > 2) {
                                    ctx.drawImage(img, (x + sx / 2) * tileSize, (y + sy / 2) * tileSize, tileSize / 2 + margin, tileSize / 2 + margin);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (zoom < 4) {
            e = getRange(p1, p2, 16, 256);
            sx = e[0];
            sy = e[1];
            ex = e[2];
            ey = e[3];

            // Render meta
            for (let x = sx; x < ex; x++) {
                for (let y = sy; y < ey; y++) {
                    let meta = getChunkMeta(x, y);
                    if (meta["team"]) {
                        ctx.save();
                        ctx.fillStyle = "red";
                        ctx.fillRect(x * 16, y * 16, 16 + margin, 16 + margin);
                        ctx.restore();
                    }
                }
            }
        }

        // Render players
        if (jsonDimensionData != null) {
            if (jsonDimensionData["players"]) {
                for (let player of jsonDimensionData["players"]) {
                    let size = 32;
                    let img = getAvatar(player["uuid"], size)

                    ctx.save();
                    ctx.shadowOffsetX = 2;
                    ctx.shadowOffsetY = 2;
                    ctx.shadowColor = 'black';
                    ctx.shadowBlur = 12;
                    ctx.drawImage(img, player["x"] - size / 2, player["z"] - size / 2, size * Math.sqrt(zoom), size * Math.sqrt(zoom));
                    ctx.restore();
                }
            }
        }
    }

    redraw();

    let lastX = canvas.width / 2, lastY = canvas.height / 2;

    let dragStart, dragged;

    canvas.addEventListener('mousedown', function (evt) {
        document.body.style.mozUserSelect = document.body.style.webkitUserSelect = document.body.style.userSelect = 'none';
        lastX = evt.offsetX || (evt.pageX - canvas.offsetLeft);
        lastY = evt.offsetY || (evt.pageY - canvas.offsetTop);
        dragStart = ctx.transformedPoint(lastX, lastY);
        dragged = false;
    }, false);

    canvas.addEventListener('mousemove', function (evt) {
        $("#tooltip-box").css({top: evt.pageY, left: evt.pageX });
        $('[data-toggle="tooltip"]').tooltip('show')

        lastX = evt.offsetX || (evt.pageX - canvas.offsetLeft);
        lastY = evt.offsetY || (evt.pageY - canvas.offsetTop);
        dragged = true;
        if (dragStart) {
            const pt = ctx.transformedPoint(lastX, lastY);
            ctx.translate(pt.x - dragStart.x, pt.y - dragStart.y);
            redraw();
        }
    }, false);

    canvas.addEventListener('mouseup', function (evt) {
        dragStart = null;
        if (!dragged) zoom(evt.shiftKey ? -1 : 1);
    }, false);

    const scaleFactor = 1.1;

    const handleScroll = function (evt) {
        const delta = evt.wheelDelta ? evt.wheelDelta / 40 : evt.detail ? -evt.detail : 0;
        if (delta) zoom(delta);
        return evt.preventDefault() && false;
    };

    canvas.addEventListener('DOMMouseScroll', handleScroll, false);
    canvas.addEventListener('mousewheel', handleScroll, false);

    async function fetchJson(url) {
        try {
            const response = await fetch(url);
            if (response.ok) {
                return await response.json();
            } else {
                console.error('Error fetching JSON:', response.status);
            }
        } catch (error) {
            console.error('Error fetching JSON:', error);
        }
        return null;
    }

    async function fetchMeta() {
        let url = "/v1/meta/" + currentServer + "/" + currentDimension;
        jsonDimensionData = (await fetchJson(url))["meta"];
        redraw();
    }

    setInterval(fetchMeta, 10000);
    await fetchMeta();

    ctx.resetTransform();
    ctx.translate(canvas.width / 2 - originX, canvas.height / 2 - originZ);
};

// Adds ctx.getTransform() - returns an SVGMatrix
// Adds ctx.transformedPoint(x,y) - returns an SVGPoint
function trackTransforms(ctx) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", 'svg');
    let xform = svg.createSVGMatrix();

    const resetTransform = ctx.resetTransform;
    ctx.resetTransform = function () {
        xform = svg.createSVGMatrix();
        resetTransform.call(ctx);
    };

    ctx.getTransform = function () {
        return xform;
    };

    const savedTransforms = [];
    const save = ctx.save;
    ctx.save = function () {
        savedTransforms.push(xform.translate(0, 0));
        return save.call(ctx);
    };

    const restore = ctx.restore;
    ctx.restore = function () {
        xform = savedTransforms.pop();
        return restore.call(ctx);
    };

    const scale = ctx.scale;
    ctx.scale = function (sx, sy) {
        xform = xform.scaleNonUniform(sx, sy);
        return scale.call(ctx, sx, sy);
    };

    const rotate = ctx.rotate;
    ctx.rotate = function (radians) {
        xform = xform.rotate(radians * 180 / Math.PI);
        return rotate.call(ctx, radians);
    };

    const translate = ctx.translate;
    ctx.translate = function (dx, dy) {
        xform = xform.translate(dx, dy);
        return translate.call(ctx, dx, dy);
    };

    const transform = ctx.transform;
    ctx.transform = function (a, b, c, d, e, f) {
        const m2 = svg.createSVGMatrix();
        m2.a = a;
        m2.b = b;
        m2.c = c;
        m2.d = d;
        m2.e = e;
        m2.f = f;
        xform = xform.multiply(m2);
        return transform.call(ctx, a, b, c, d, e, f);
    };

    const setTransform = ctx.setTransform;
    ctx.setTransform = function (a, b, c, d, e, f) {
        xform.a = a;
        xform.b = b;
        xform.c = c;
        xform.d = d;
        xform.e = e;
        xform.f = f;
        return setTransform.call(ctx, a, b, c, d, e, f);
    };

    const pt = svg.createSVGPoint();
    ctx.transformedPoint = function (x, y) {
        pt.x = x;
        pt.y = y;
        return pt.matrixTransform(xform.inverse());
    }
}