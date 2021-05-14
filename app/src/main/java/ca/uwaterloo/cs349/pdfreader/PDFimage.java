package ca.uwaterloo.cs349.pdfreader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Stack;

@SuppressLint("AppCompatCustomView")
public class PDFimage extends ImageView {

    final String LOGNAME = "pdf_image";

    // drawing path
    Path path = null;
    HashMap<Integer, Pair<ArrayList<Path>, ArrayList<Path>>> pathMap = new HashMap<>(); // <index, <pen, marker>>
    int currentIndex;

    // image to display
    enum Tool {PEN, MARKER, ERASER}
    Bitmap bitmap;
    Paint paint, pen, marker;
    Tool tool;

    // undo redo
    public static class UndoablePath {
        Path path;
        Tool original;
        Tool command;

        public UndoablePath(Path path, Tool original, Tool command) {
            this.path = path;
            this.original = original;
            this.command = command;
        }

        public UndoablePath reverse() {
            return new UndoablePath(path, command, original);
        }
    }

    public static class UndoableEdit {
        int index;
        ArrayList<UndoablePath> paths; // path group edited

        public UndoableEdit(int index, ArrayList<UndoablePath> paths) {
            this.index = index;
            this.paths = paths;
        }

        public UndoableEdit reverse() {
            ArrayList<UndoablePath> reversedPaths = new ArrayList<>();
            for (UndoablePath p : paths) {
                reversedPaths.add(p.reverse());
            }
            return new UndoableEdit(index, reversedPaths);
        }
    }
    Stack<UndoableEdit> undoStack = new Stack<>(); // reverse record
    Stack<UndoableEdit> redoStack = new Stack<>(); // original record

    // constructor
    public PDFimage(Context context) {
        super(context);

        pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        pen.setColor(Color.BLUE);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(5);

        marker = new Paint(Paint.ANTI_ALIAS_FLAG);
        marker.setColor(Color.YELLOW);
        marker.setStyle(Paint.Style.STROKE);
        marker.setStrokeWidth(30);
        marker.setAlpha(150);
    }

    // set image as background
    public void setImage(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    // set brush characteristics
    // e.g. color, thickness, alpha
    public void setTool(Tool tool) {
        this.tool = tool;
        switch (tool) {
            case PEN:
                paint = pen;
                break;
            case MARKER:
                paint = marker;
                break;
            default:
                paint = null;
        }
    }

    public void setCurrentIndex(int index) {
        currentIndex = index;
        if (!pathMap.containsKey(index)) {
            pathMap.put(index, new Pair<>(new ArrayList<Path>(), new ArrayList<Path>()));
        }
    }

    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Log.d(LOGNAME, "Action down");
                if (tool == Tool.ERASER) {
                    ArrayList<UndoablePath> modifiedPaths = erase(event.getX(), event.getY());
                    undoStack.push(new UndoableEdit(currentIndex, modifiedPaths));
                    redoStack.clear();
                } else if (tool == Tool.PEN || tool == Tool.MARKER) {
                    path = new Path();
                    path.moveTo(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_MOVE:
                Log.d(LOGNAME, "Action move");
                if (tool == Tool.PEN || tool == Tool.MARKER) {
                    path.lineTo(event.getX(), event.getY());
                }
                break;
            case MotionEvent.ACTION_UP:
                Log.d(LOGNAME, "Action up");
                if (tool == Tool.PEN) {
                    pathMap.get(currentIndex).first.add(path);
                    undoStack.push(new UndoableEdit(currentIndex, new ArrayList<>(Arrays.asList(new UndoablePath(path, tool, Tool.ERASER)))));
                    redoStack.clear();
                } else if (tool == Tool.MARKER) {
                    pathMap.get(currentIndex).second.add(path);
                    undoStack.push(new UndoableEdit(currentIndex, new ArrayList<>(Arrays.asList(new UndoablePath(path, tool, Tool.ERASER)))));
                    redoStack.clear();
                }
                break;
        }
        return true;
    }

    public int undo() {
        if (!undoStack.empty()) {
            UndoableEdit edit = undoStack.pop();
            for (UndoablePath p : edit.paths) {
                switch (p.command) {
                    case PEN:
                        pathMap.get(edit.index).first.add(p.path);
                        redoStack.push(edit.reverse());
                        break;
                    case MARKER:
                        pathMap.get(edit.index).second.add(p.path);
                        redoStack.push(edit.reverse());
                        break;
                    case ERASER:
                        if (p.original == Tool.PEN) {
                            pathMap.get(edit.index).first.remove(p.path);
                        } else if (p.original == Tool.MARKER) {
                            pathMap.get(edit.index).second.remove(p.path);
                        }
                        redoStack.push(edit.reverse());
                        break;
                }
            }
            return edit.index;
        }
        return -1;
    }

    public int redo() {
        if (!redoStack.empty()) {
            UndoableEdit edit = redoStack.pop();
            for (UndoablePath p : edit.paths) {
                switch (p.command) {
                    case PEN:
                        pathMap.get(edit.index).first.add(p.path);
                        undoStack.push(edit.reverse());
                        break;
                    case MARKER:
                        pathMap.get(edit.index).second.add(p.path);
                        undoStack.push(edit.reverse());
                        break;
                    case ERASER:
                        if (p.original == Tool.PEN) {
                            pathMap.get(edit.index).first.remove(p.path);
                            undoStack.push(edit.reverse());
                        } else if (p.original == Tool.MARKER) {
                            pathMap.get(edit.index).second.remove(p.path);
                            undoStack.push(edit.reverse());
                        }
                        break;
                }
            }
            return edit.index;
        }
        return -1;
    }

    public ArrayList<UndoablePath> erase(float x, float y) {
        final int radius = 20;
        final int maxLayout = 2000;

        ArrayList<Path> penPaths = new ArrayList<>();
        ArrayList<Path> markerPaths = new ArrayList<>();
        ArrayList<UndoablePath> undoablePaths = new ArrayList<>();
        Path touchPointPath = new Path();

        // find overlap, reference: https://stackoverflow.com/questions/11184397/path-intersection-in-android
        for (Path p : pathMap.get(currentIndex).first) {
            touchPointPath.addCircle(x, y, radius, Path.Direction.CW);
            Region clip = new Region(0, 0, maxLayout, maxLayout);
            Region regionTouch = new Region();
            regionTouch.setPath(touchPointPath, clip);
            Region regionPath = new Region();
            regionPath.setPath(p, clip);
            if (!regionTouch.quickReject(regionPath) && regionTouch.op(regionPath, Region.Op.INTERSECT)) {
                penPaths.add(p);
            }
        }
        for (Path p : pathMap.get(currentIndex).second) {
            touchPointPath.addCircle(x, y, radius, Path.Direction.CW);
            Region clip = new Region(0, 0, maxLayout, maxLayout);
            Region regionTouch = new Region();
            regionTouch.setPath(touchPointPath, clip);
            Region regionPath = new Region();
            regionPath.setPath(p, clip);
            if (!regionTouch.quickReject(regionPath) && regionTouch.op(regionPath, Region.Op.INTERSECT)) {
                markerPaths.add(p);
            }
        }

        // remove
        for (Path p : penPaths) {
            pathMap.get(currentIndex).first.remove(p);
            undoablePaths.add(new UndoablePath(p, Tool.ERASER, Tool.PEN));
        }
        for (Path p : markerPaths) {
            pathMap.get(currentIndex).second.remove(p);
            undoablePaths.add(new UndoablePath(p, Tool.ERASER, Tool.MARKER));
        }

        return undoablePaths;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // draw background
        if (bitmap != null) {
            this.setImageBitmap(bitmap);
        }
        // draw lines over it
        for (Path path : pathMap.get(currentIndex).first) {
            canvas.drawPath(path, pen);
        }
        for (Path path : pathMap.get(currentIndex).second) {
            canvas.drawPath(path, marker);
        }
        super.onDraw(canvas);
    }
}
