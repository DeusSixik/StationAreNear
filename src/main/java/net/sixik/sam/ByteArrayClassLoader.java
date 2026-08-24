package net.sixik.sam;

final class ByteArrayClassLoader extends ClassLoader {
    private final byte[] classBytes;

    ByteArrayClassLoader(byte[] classBytes) {
        super(ByteArrayClassLoader.class.getClassLoader());
        this.classBytes = classBytes;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!"samtool.SamClass".equals(name)) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, classBytes, 0, classBytes.length);
    }
}
