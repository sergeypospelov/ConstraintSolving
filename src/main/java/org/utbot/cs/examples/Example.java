package org.utbot.cs.examples;

public class Example {
    public static class Inner {
        int x;
        int y;
        Inner inner;
    }

    public int funCall(boolean a) {
        Inner c = new Inner();
        int t = 0;
        if (a) {
            t += testMe(c);
        }
        return t;
    }

    public int testMe(Object in) {
        return in.hashCode();
    }

    public int bytesMagic(byte a, byte b) {
        byte b1 = 0;
        if (a >= 0) {
            b1 += a;
        }
        if (b1 < 0) {
            return 1;
        }
        if (b >= 0) {
            b1 += b;
        }
        if (b1 < 0) {
            return 2;
        }

        return 3;
    }

    public int switchTest(int a, int b) {
        int c;
        if (a == 0) {
            return -1;
        }
        if (a + 5 == b) {
            return 0;
        }
        int d = -1;
        switch (a) {
            case 1 -> {
                String s = take();
                return 1;
            }
            case 2 -> {
                return 2;
            }
            case 3 -> {
                return 3;
            }
            default -> {
                c = 5;
                d = b;
            }
        }
        if (a + c == b) {
            return 4;
        }
        if (b + a == c) {
            return 5;
        }
        return 6;
    }

    public String nullTest(int a) {
        String s = "a";
        if (a == 1) {
            s = null;
        } else {
            s = "b";
        }
        return s;
    }

    String take() {
        return "Hello";
    }

    int[] tmp = new int[10];

    private boolean privateReturnsTrue() {
        return true;
    }

    public int A(int x) {
        if (B(x) == 10) {
            return 1;
        } else {
            return 0;
        }
    }

    public int B(int x) {
        if (x == 1) {
            return 1;
        }
        return C(x - 1, 10) * x;
    }

    public int C(int x, int y) {
        return x % 2 + y;

    }

    public int D(int x) {
        return x;
    }

    public int merge(boolean b1, boolean b2, boolean b3, boolean b4, boolean b5) {
        int c = (b5 ? 1 : 0) - (b4 ? 1 : 0);
        if (b1) {
            c += 1;
        }
        if (b2) {
            c += 2;
        }
        if (b3) {
            c += 4;
        }
        if (b4) {
            c += 8;
        }
        if (b5) {
            c += 16;
        }
        if (c == 17) {
            return 0;
        }
        if (c == 0) {
            return 1;
        }
        c++;
        if (c == 1 || c == 18 || c == 8) {
            return 2;
        }
        return 3;
    }

    public int ifs(Inner a, Inner b) {
        if (b.x == 0) {
            return 0;
        }
        go(a);
        if (b.x == 0 && a != b) {
            return b.y;
        }
        return 2;
    }

    public void go(Inner a) {
        if (a.y == 0) {
            return;
        }
        a.inner.x = 0;
        go(a.inner);
        bar(a);
    }

    public void bar(Inner a) {
        a.inner.y = 1337;
    }

    public int kek(int x) {
        foo(kek(x));
        return 1;
    }

    public byte foo(long x) {
        byte t = 1;
        return t;
    }

    public Example kek2() {
        return null;
    }

    public static void main(String[] args) {
        Example e = new Example();
        //int res = e.ifs(true, true, 613566757, null);
//        System.out.println(res);
    }
}
