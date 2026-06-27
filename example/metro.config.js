const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');
const path = require('path');
const root = path.resolve(__dirname, '..');
const pak = require('../package.json');
const exclusionList = require('metro-config/src/defaults/exclusionList');

const escape = (string) => string.replace(/[|\\{}()[\]^$+*?.]/g, '\\$&');
const modules = Object.keys({ ...pak.peerDependencies });

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const config = {
  watchFolders: [root],
  resolver: {
    unstable_enablePackageExports: false,
    blacklistRE: exclusionList(
      modules.map(
        (m) => new RegExp(`^${escape(path.join(root, 'node_modules', m))}\\/.*$`)
      )
    ),
    extraNodeModules: modules.reduce((acc, name) => {

      acc[name] = path.join(__dirname, 'node_modules', name);
      return acc;
    }, {
      [pak.name]: root
    }),
    nodeModulesPaths: [
      path.resolve(__dirname, 'node_modules'),
      path.resolve(root, 'node_modules'),
    ],
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
