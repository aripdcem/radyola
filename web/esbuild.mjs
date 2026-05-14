import esbuild from 'esbuild';

esbuild
    .build({
        entryPoints: ['src/index.js'],
        outdir: 'dist/js',
        bundle: true,
        sourcemap: true,
        //loader: { '.png': 'dataurl' }, // Converts to data url in JS bundle
        loader: { '.png': 'file' }, // Copies to output folder
        minify: true,
        splitting: true,
        format: 'esm',
        target: ['esnext']
    })
    .catch(() => process.exit(1));